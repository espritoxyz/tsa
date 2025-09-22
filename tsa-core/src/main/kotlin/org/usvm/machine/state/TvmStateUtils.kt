package org.usvm.machine.state

import io.ksmt.expr.KBitVecValue
import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KBvSort
import io.ksmt.utils.powerOfTwo
import kotlinx.collections.immutable.toPersistentList
import org.ton.bitstring.BitString
import org.ton.bytecode.BALANCE_PARAMETER_IDX
import org.ton.bytecode.MethodId
import org.ton.bytecode.TsaArtificialExitInst
import org.ton.bytecode.TsaArtificialJmpToContInst
import org.ton.bytecode.TsaArtificialOnComputePhaseExitInst
import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.TvmCellValue
import org.ton.bytecode.TvmExceptionContinuation
import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmMethod
import org.ton.bytecode.TvmOrdContinuation
import org.ton.cell.Cell
import org.ton.hashmap.HashMapE
import org.usvm.NULL_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.isAllocated
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.ADDRESS_BITS
import org.usvm.machine.TvmContext.Companion.dictKeyLengthField
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.intValue
import org.usvm.machine.maxUnsignedValue
import org.usvm.machine.state.TvmPhase.ACTION_PHASE
import org.usvm.machine.state.TvmPhase.BOUNCE_PHASE
import org.usvm.machine.state.TvmPhase.COMPUTE_PHASE
import org.usvm.machine.state.TvmStack.TvmStackTupleValueConcreteNew
import org.usvm.machine.toTvmCell
import org.usvm.machine.types.TvmBuilderType
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmDataCellType
import org.usvm.machine.types.TvmDictCellType
import org.usvm.machine.types.TvmFinalReferenceType
import org.usvm.machine.types.TvmNullType
import org.usvm.machine.types.TvmSliceType
import org.usvm.machine.types.TvmType
import org.usvm.memory.GuardedExpr
import org.usvm.memory.foldHeapRef
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGeExpr
import org.usvm.mkSizeLeExpr
import org.usvm.sizeSort
import org.usvm.test.resolver.HashMapESerializer
import org.usvm.types.USingleTypeStream
import java.math.BigInteger

val TvmState.lastStmt get() = pathNode.statement

fun TvmState.newStmt(stmt: TvmInst) {
    pathNode += stmt
}

fun TvmInst.nextStmt(): TvmInst =
    location.codeBlock.instList.getOrNull(location.index + 1)
        ?: error("Unexpected end of the code block ${location.codeBlock}")

fun TvmState.c2IsDefault(): Boolean {
    val c2 = registersOfCurrentContract.c2.value
    return c2 == TvmExceptionContinuation
}

fun TvmContext.setFailure(
    failure: TvmMethodResult.TvmErrorExit,
    level: TvmFailureType = TvmFailureType.UnknownError,
    param: UExpr<TvmInt257Sort> = zeroValue,
    implicitThrow: Boolean = true,
): (TvmState) -> Unit =
    { state ->
        if (implicitThrow) {
            state.consumeGas(IMPLICIT_EXCEPTION_THROW_GAS)
        }

        // Throwing exception clears the current stack and pushes its parameter and exit code
        state.stack.clear()
        state.stack.addInt(param)
        with(state.ctx) {
            state.stack.addInt(failure.exitCode.toBv257())
        }

        val c2 = state.registersOfCurrentContract.c2.value
        if (state.c2IsDefault()) {
            state.setExit(TvmMethodResult.TvmFailure(failure, level, state.phase, state.stack, state.pathNode))
        } else {
            state.newStmt(TsaArtificialJmpToContInst(c2, state.lastStmt.location))
        }
    }

fun TvmState.setExit(methodResult: TvmMethodResult) {
    if (methodResult.isExceptional()) {
        isExceptional = true
    }
    when (phase) {
        COMPUTE_PHASE -> newStmt(TsaArtificialOnComputePhaseExitInst(methodResult, lastStmt.location))
        ACTION_PHASE -> newStmt(TsaArtificialExitInst(methodResult, lastStmt.location))
        BOUNCE_PHASE -> newStmt(TsaArtificialExitInst(methodResult, lastStmt.location))
        else -> error("Unexpected exit on phase: $phase")
    }
}

fun <R> TvmStepScopeManager.calcOnStateCtx(block: context(TvmContext) TvmState.() -> R): R =
    calcOnState {
        block(ctx, this)
    }

fun <R> TvmStepScopeManager.doWithCtx(block: context(TvmContext) TvmStepScopeManager.() -> R): R {
    val ctx = calcOnState { ctx }
    return block(ctx, this)
}

fun TvmStepScopeManager.doWithStateCtx(block: context(TvmContext) TvmState.() -> Unit) =
    doWithState {
        block(ctx, this)
    }

fun TvmState.generateSymbolicCell(): UConcreteHeapRef =
    generateSymbolicRef(TvmCellType).also { initializeSymbolicCell(it) }

fun TvmState.ensureSymbolicCellInitialized(ref: UHeapRef) =
    ensureSymbolicRefInitialized(ref, TvmCellType) { initializeSymbolicCell(it) }

fun TvmState.generateSymbolicSlice(): UConcreteHeapRef =
    generateSymbolicRef(TvmSliceType).also { initializeSymbolicSlice(it) }

fun TvmState.ensureSymbolicSliceInitialized(ref: UHeapRef) =
    ensureSymbolicRefInitialized(ref, TvmSliceType) { initializeSymbolicSlice(it) }

fun TvmState.initializeSymbolicCell(cell: UConcreteHeapRef) =
    with(ctx) {
        val refsLength = memory.readField(cell, TvmContext.cellRefsLengthField, sizeSort)

        pathConstraints += mkSizeLeExpr(refsLength, maxRefsLengthSizeExpr)
        pathConstraints += mkSizeGeExpr(refsLength, zeroSizeExpr)
    }

fun TvmState.initializeSymbolicSlice(ref: UConcreteHeapRef) =
    with(ctx) {
        // Assume that all input slices were not read, that means dataPos == 0 and refsPos == 0
        fieldManagers.cellDataLengthFieldManager.writeSliceDataPos(memory, ref, zeroSizeExpr)
        memory.writeField(ref, TvmContext.sliceRefPosField, sizeSort, zeroSizeExpr, guard = trueExpr)

        // Cell in input slices must be represented with static refs to be correctly processed in TvmCellRefsRegion
        val cell = generateSymbolicCell()
        memory.writeField(ref, TvmContext.sliceCellField, addressSort, cell, guard = trueExpr)
        memory.types.allocate(cell.address, TvmDataCellType)
    }

fun TvmState.generateSymbolicBuilder(): UConcreteHeapRef =
    generateSymbolicRef(TvmBuilderType).also { initializeSymbolicBuilder(it) }

fun TvmState.ensureSymbolicBuilderInitialized(ref: UHeapRef) =
    ensureSymbolicRefInitialized(ref, TvmBuilderType) { initializeSymbolicBuilder(it) }

fun TvmState.initializeSymbolicBuilder(ref: UConcreteHeapRef) =
    with(ctx) {
//    // TODO hack! Assume that all input builder were not written, that means dataLength == 0 and refsLength == 0
//    memory.writeField(ref, TvmContext.cellDataLengthField, sizeSort, mkSizeExpr(0), guard = trueExpr)
//    memory.writeField(ref, TvmContext.cellRefsLengthField, sizeSort, mkSizeExpr(0), guard = trueExpr)
    }

fun TvmStepScopeManager.assertIfSat(constraint: UBoolExpr): Boolean {
    val originalState = calcOnState { this }
    val (stateWithConstraint) = originalState.ctx.statesForkProvider.forkMulti(originalState, listOf(constraint))
    return stateWithConstraint != null
}

fun TvmContext.signedIntegerFitsBits(
    value: UExpr<TvmInt257Sort>,
    bits: UInt,
): UBoolExpr =
    when {
        bits == 0u -> value eq zeroValue
        bits >= TvmContext.INT_BITS -> trueExpr
        else ->
            mkAnd(
                mkBvSignedLessOrEqualExpr(value, powerOfTwo(bits - 1u).minus(BigInteger.ONE).toBv257()),
                mkBvSignedGreaterOrEqualExpr(value, powerOfTwo(bits - 1u).negate().toBv257()),
            )
    }

/**
 * Since TVM integers have a signed representation only, every non-negative integer fits in 256 bits
 */
fun TvmContext.unsignedIntegerFitsBits(
    value: UExpr<TvmInt257Sort>,
    bits: UInt,
): UBoolExpr =
    when {
        bits == 0u -> value eq zeroValue
        bits >= TvmContext.INT_BITS - 1u -> mkBvSignedGreaterOrEqualExpr(value, zeroValue)
        else ->
            mkAnd(
                mkBvSignedLessOrEqualExpr(value, maxUnsignedValue(bits).toBv257()),
                mkBvSignedGreaterOrEqualExpr(value, zeroValue),
            )
    }

/**
 * 0 <= [sizeBits] <= 257
 */
fun TvmContext.signedIntegerFitsBits(
    value: UExpr<TvmInt257Sort>,
    bits: UExpr<TvmInt257Sort>,
): UBoolExpr =
    mkAnd(
        mkBvSignedLessOrEqualExpr(bvMinValueSignedExtended(bits), value),
        mkBvSignedLessOrEqualExpr(value, bvMaxValueSignedExtended(bits)),
    )

/**
 * 0 <= [sizeBits] <= 256
 *
 * @see unsignedIntegerFitsBits
 */
fun TvmContext.unsignedIntegerFitsBits(
    value: UExpr<TvmInt257Sort>,
    bits: UExpr<TvmInt257Sort>,
): UBoolExpr =
    mkAnd(
        mkBvSignedLessOrEqualExpr(zeroValue, value),
        mkBvSignedLessOrEqualExpr(value, bvMaxValueUnsignedExtended(bits)),
    )

/**
 * 0 <= [sizeBits] <= 257
 */
fun <Sort : KBvSort> TvmContext.bvMinValueSignedExtended(sizeBits: UExpr<Sort>): UExpr<Sort> {
    val zero = mkBv(0, sizeBits.sort)
    val one = mkBv(1, sizeBits.sort)
    return mkIte(
        condition = sizeBits eq zero,
        trueBranch = zero,
        falseBranch = mkBvNegationExpr(mkBvShiftLeftExpr(one, mkBvSubExpr(sizeBits, one))),
    )
}

/**
 * 0 <= [sizeBits] <= 257
 */
fun <Sort : KBvSort> TvmContext.bvMaxValueSignedExtended(sizeBits: UExpr<Sort>): UExpr<Sort> {
    val zero = mkBv(0, sizeBits.sort)
    val one = mkBv(1, sizeBits.sort)
    return mkIte(
        condition = sizeBits eq zero,
        trueBranch = zero,
        falseBranch = mkBvSubExpr(mkBvShiftLeftExpr(one, mkBvSubExpr(sizeBits, one)), one),
    )
}

/**
 * 0 <= [sizeBits] <= 256
 *
 * @see unsignedIntegerFitsBits
 */
fun TvmContext.bvMaxValueUnsignedExtended(sizeBits: UExpr<TvmInt257Sort>): UExpr<TvmInt257Sort> =
    mkBvSubExpr(mkBvShiftLeftExpr(oneValue, sizeBits), oneValue)

fun TvmState.calcConsumedGas(): UExpr<TvmSizeSort> =
    gasUsageHistory.fold(ctx.zeroSizeExpr) { acc, value -> ctx.mkSizeAddExpr(acc, value) }

/**
 * @property eventBegin is the beginning time of the event (see docs of [TvmMessageDrivenContractExecutionEntry])
 * @property eventEnd is an exclusive end boundary of `gasUsageHistory`.
 * After execution of the control-changing instruction X, we have already appended the
 * gas that accounts for X instruction, and we write `gasUsageHistory.size` as the previous event end
 * and current event begin. Thus, the eventEnd index withing gasUsageHistory does not belong to the
 * corresponding phase and is an exclusive boundary.
 */
fun TvmState.calcPhaseConsumedGas(
    eventBegin: Int,
    eventEnd: Int,
): UExpr<TvmSizeSort> =
    gasUsageHistory.subList(eventBegin, eventEnd).fold(ctx.zeroSizeExpr) { acc, value -> ctx.mkSizeAddExpr(acc, value) }

private data class RefInfo(
    val type: TvmType,
    val ref: UConcreteHeapRef,
    val guard: UBoolExpr,
)

private fun TvmState.getRefLeaves(value: UHeapRef): List<RefInfo> {
    val refHandler = { acc: MutableList<RefInfo>, ref: GuardedExpr<UConcreteHeapRef> ->
        val cur = memory.types.getTypeStream(ref.expr)
        require(cur is USingleTypeStream)
        acc += RefInfo(cur.commonSuperType, ref.expr, ref.guard)
        acc
    }
    return foldHeapRef(
        ref = value,
        initial = mutableListOf(),
        initialGuard = ctx.trueExpr,
        collapseHeapRefs = false,
        staticIsConcrete = true,
        blockOnConcrete = refHandler,
        blockOnSymbolic = { _, ref -> error("Unexpected symbolic ref ${ref.expr}") },
    )
}

fun TvmState.assertType(
    value: UHeapRef,
    type: TvmType,
) {
    check(type !is TvmDictCellType && type !is TvmDataCellType) {
        "For asserting TvmDictCellType or TvmDataCellType, use special methods"
    }

    if (value is UConcreteHeapRef && value.address == NULL_ADDRESS) {
        require(type is TvmNullType)
        return
    }
    val refOldTypes = getRefLeaves(value)
    refOldTypes.forEach { (oldType, ref) ->
        if (typeSystem.isSupertype(oldType, type)) {
            memory.types.allocate(ref.address, type)
        } else if (!typeSystem.isSupertype(type, oldType)) {
            throw TypeCastException(oldType, type)
        }
    }
}

private fun TvmState.extractFullCellIfItIsConcrete(ref: UConcreteHeapRef): Cell? =
    with(ctx) {
        if (!ref.isAllocated) {
            return null
        }

        val data =
            fieldManagers.cellDataFieldManager.readCellDataForBuilderOrAllocatedCell(
                this@extractFullCellIfItIsConcrete,
                ref,
            )
        val dataLength =
            fieldManagers.cellDataLengthFieldManager.readCellDataLength(this@extractFullCellIfItIsConcrete, ref)
        val refsLength = memory.readField(ref, TvmContext.cellRefsLengthField, sizeSort)

        if (data !is KInterpretedValue || dataLength !is KInterpretedValue || refsLength !is KInterpretedValue) {
            return null
        }

        val children =
            List(refsLength.intValue()) { i ->
                val child = readCellRef(ref, i.toBv()) as UConcreteHeapRef
                extractFullCellIfItIsConcrete(child)
                    ?: return@with null
            }

        val dataStr = (data as KBitVecValue).stringValue.take(dataLength.intValue()).map { it == '1' }

        return Cell(BitString.of(dataStr), *children.toTypedArray())
    }

/**
 * Return true if transformed.
 * */
private fun TvmState.transformToConcreteDictIfPossible(
    ref: UConcreteHeapRef,
    keyLength: Int,
): Boolean =
    with(ctx) {
        val oldType = getRefLeaves(ref).single().type
        check(oldType is TvmDataCellType) {
            "Unexpected type in transformToConcreteDictIfPossible: $oldType"
        }

        val cell =
            extractFullCellIfItIsConcrete(ref)
                ?: return false

        val codec = HashMapE.tlbCodec(keyLength, HashMapESerializer)
        val parsedDict =
            kotlin
                .runCatching {
                    codec.loadTlb(Cell(BitString(true), cell))
                }.getOrElse {
                    return false
                }

        memory.types.allocate(ref.address, TvmDictCellType)
        memory.writeField(ref, dictKeyLengthField, sizeSort, mkSizeExpr(keyLength), guard = trueExpr)

        val content =
            parsedDict.map { (keyBitString, valueCell) ->
                val cellRef = allocateCell(valueCell.toTvmCell())
                val sliceValue = allocSliceFromCell(cellRef)
                val key = mkBv(keyBitString.toBinary(), keyLength.toUInt())
                key to sliceValue
            }

        initializeConcreteDict(ref, DictId(keyLength), content, mkBvSort(keyLength.toUInt()))

        return true
    }

private fun TvmStepScopeManager.assertConcreteCellType(
    value: UHeapRef,
    newType: TvmType,
    badType: TvmFinalReferenceType,
    exit: TvmMethodResult.TvmSoftFailureExit,
): Unit? {
    val refOldTypes = calcOnState { getRefLeaves(value) }
    val badCellTypeGuard =
        doWithCtx {
            refOldTypes.fold(falseExpr as UBoolExpr) { acc, info ->
                if (info.type != badType) {
                    acc
                } else {
                    acc or info.guard
                }
            }
        }
    fork(
        ctx.mkNot(badCellTypeGuard),
        falseStateIsExceptional = true,
        blockOnFalseState = {
            setExit(TvmMethodResult.TvmSoftFailure(exit, calcOnState { phase }, stack))
        },
    ) ?: return null

    doWithState {
        refOldTypes.forEach { (oldType, ref) ->
            if (oldType == badType) {
                // do nothing
            } else if (typeSystem.isSupertype(oldType, newType)) {
                memory.types.allocate(ref.address, newType)
            } else if (!typeSystem.isSupertype(newType, oldType)) {
                throw TypeCastException(oldType, newType)
            }
        }
    }

    return Unit
}

fun TvmStepScopeManager.assertDictType(
    value: UHeapRef,
    keyLength: Int,
): Unit? {
    val refs = calcOnState { getRefLeaves(value) }
    refs.forEach { info ->
        if (info.type == TvmDataCellType) {
            calcOnState { transformToConcreteDictIfPossible(info.ref, keyLength) }
        }
    }
    return assertConcreteCellType(
        value,
        newType = TvmDictCellType,
        badType = TvmDataCellType,
        TvmDictOperationOnDataCell,
    )
}

fun TvmStepScopeManager.assertDataCellType(value: UHeapRef): Unit? =
    assertConcreteCellType(
        value,
        newType = TvmDataCellType,
        badType = TvmDictCellType,
        TvmDataCellOperationOnDict,
    )

fun TvmStepScopeManager.killCurrentState() =
    doWithCtx {
        assert(falseExpr).also {
            check(it == null) {
                "Unexpected not null [assert(falseExpr)] result"
            }
        }
    }

fun initializeContractExecutionMemory(
    contractsCode: List<TsaContractCode>,
    state: TvmState,
    contractId: ContractId,
    newMsgValue: UExpr<TvmInt257Sort>?,
    allowInputStackValues: Boolean,
): TvmContractExecutionMemory {
    val contractCode = contractsCode[contractId]
    val ctx = state.ctx
    val c4 =
        state.contractIdToC4Register[contractId]
            ?: error("c4 for contract $contractId is not found")

    val stack = TvmStack(ctx, allowInputValues = allowInputStackValues)

    val firstElementOfC7 =
        with(ctx) {
            val oldFirstElementOfC7 =
                state.contractIdToFirstElementOfC7[contractId]
                    ?: error("First element of c7 for contract $contractId not found")

            if (newMsgValue != null) {
                val oldBalance =
                    oldFirstElementOfC7[BALANCE_PARAMETER_IDX, stack]
                        .cell(stack)
                        ?.tupleValue
                        ?.get(0, stack)
                        ?.cell(stack)
                        ?.intValue
                        ?: error("Cannot extract old balance from oldFirstElementOfC7")
                val newBalance = mkBvAddExpr(oldBalance, newMsgValue)
                val newEntries =
                    oldFirstElementOfC7.entries.mapIndexed { index, entry ->
                        if (index == BALANCE_PARAMETER_IDX) {
                            TvmStack.TvmConcreteStackEntry(makeBalanceEntry(ctx, newBalance))
                        } else {
                            entry
                        }
                    }
                val newFirstElementOfC7 = TvmStackTupleValueConcreteNew(ctx, newEntries.toPersistentList())
                state.contractIdToFirstElementOfC7 =
                    state.contractIdToFirstElementOfC7.put(contractId, newFirstElementOfC7)
                newFirstElementOfC7
            } else {
                oldFirstElementOfC7
            }
        }

    return TvmContractExecutionMemory(
        stack,
        TvmRegisters(
            ctx,
            C0Register(ctx.quit0Cont),
            C1Register(ctx.quit1Cont),
            C2Register(TvmExceptionContinuation),
            C3Register(TvmOrdContinuation(contractCode.mainMethod, contractCode.codeCell), contractCode),
            c4,
            C5Register(TvmCellValue(state.allocEmptyCell())),
            C7Register(state.initC7(firstElementOfC7)),
        ),
    )
}

fun TvmState.contractEpilogue(isChecker: Boolean) {
    contractIdToFirstElementOfC7 =
        contractIdToFirstElementOfC7.put(
            currentContract,
            registersOfCurrentContract.c7.value[0, stack].cell(stack) as TvmStackTupleValueConcreteNew,
        )
    if (isChecker) {
        checkerC7 = registersOfCurrentContract.c7
    }
    val commitedState =
        lastCommitedStateOfContracts[currentContract]
            ?: return

    contractIdToC4Register = contractIdToC4Register.put(currentContract, commitedState.c4)
    // last commited state is cleared, as [currentContract] can be visited multiple times
    lastCommitedStateOfContracts = lastCommitedStateOfContracts.remove(currentContract)
}

fun TvmState.switchToFirstMethodInContract(
    contractCode: TsaContractCode,
    methodId: MethodId,
) = with(ctx) {
    if (tvmOptions.useMainMethodForInitialMethodJump) {
        val methodIdAsInt = methodId.toBv257()
        stack.addInt(methodIdAsInt)
        newStmt(contractCode.mainMethod.instList.first())
    } else {
        val method =
            contractCode.methods[methodId]
                ?: error("Method $methodId not found")
        newStmt(method.instList.first())
    }
}

fun TvmState.switchDirectlyToMethodInContract(method: TvmMethod) = newStmt(method.instList.first())

// second value is workchain
fun TvmState.generateSymbolicAddressCell(): Pair<UConcreteHeapRef, UExpr<UBvSort>> =
    with(ctx) {
        val workchain = mkBv(0, 8u) // TODO: consider other workchains?
        val address =
            allocDataCellFromData(
                mkBvConcatExpr(
                    mkBvConcatExpr(
                        // addr_std$10 anycast:(Maybe Anycast)
                        mkBv("100", 3u),
                        // workchain_id:int8
                        workchain,
                    ),
                    // address:bits256
                    makeSymbolicPrimitive(mkBvSort(ADDRESS_BITS.toUInt())),
                ),
            )
        return address to workchain
    }

/**
 * @return null if the method was not called
 */
fun TvmState.callCheckerMethodIfExists(
    methodId: MethodId,
    returnStmt: TvmInst,
    contractsCode: List<TsaContractCode>,
    pushArgumentsOnStack: TvmState.() -> Unit,
): Unit? {
    val checkerContractIds =
        contractsCode.mapIndexedNotNull { index, code ->
            if (code.isContractWithTSACheckerFunctions) index to code else null
        }
    if (checkerContractIds.size >= 2) {
        error("Too many checker contracts (ids: ${checkerContractIds.map { it.first }})")
    }
    val (checkerContractId, checkerCode) = checkerContractIds.singleOrNull() ?: return null
    require(checkerContractId != currentContract) {
        "calCheckerMethod is expected to be called outside of checker"
    }
    val method = checkerCode.methods[methodId] ?: return null
    // after this point, we always return non-null

    val oldMemory = TvmContractExecutionMemory(stack, registersOfCurrentContract)
    contractStack =
        contractStack.add(
            TvmEventInformation(
                currentContract,
                returnStmt,
                oldMemory,
                0,
                currentEventId,
                receivedMessage,
                computeFeeUsed,
                isExceptional,
            ),
        )
    val executionMemory =
        initializeContractExecutionMemory(
            contractsCode,
            this,
            checkerContractId,
            null,
            allowInputStackValues = true,
        )

    isExceptional = false
    currentContract = checkerContractId
    registersOfCurrentContract = executionMemory.registers
    val storedC7 = checkerC7
    if (storedC7 != null) {
        registersOfCurrentContract.c7 = storedC7
    }
    pushArgumentsOnStack()
    switchDirectlyToMethodInContract(method)
    return Unit
}
