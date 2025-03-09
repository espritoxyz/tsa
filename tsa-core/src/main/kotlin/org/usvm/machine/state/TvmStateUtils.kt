package org.usvm.machine.state

import io.ksmt.sort.KBvSort
import io.ksmt.utils.powerOfTwo
import org.ton.bytecode.MethodId
import org.ton.bytecode.TsaArtificialJmpToContInst
import org.ton.bytecode.TvmCellValue
import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.TvmExceptionContinuation
import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmOrdContinuation
import org.usvm.NULL_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.types.TvmBuilderType
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmDataCellType
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
import org.usvm.types.USingleTypeStream
import java.math.BigInteger
import org.ton.bytecode.TsaArtificialActionPhaseInst
import org.ton.bytecode.TsaArtificialExitInst
import org.usvm.machine.interpreter.TvmInterpreter.Companion.logger
import org.usvm.machine.maxUnsignedValue
import org.usvm.machine.state.TmvPhase.ACTION_PHASE
import org.usvm.machine.state.TmvPhase.COMPUTE_PHASE
import org.usvm.machine.state.TvmStack.TvmStackTupleValueConcreteNew

val TvmState.lastStmt get() = pathNode.statement
fun TvmState.newStmt(stmt: TvmInst) {
    pathNode += stmt
}

fun TvmInst.nextStmt(): TvmInst = location.codeBlock.instList.getOrNull(location.index + 1)
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
): (TvmState) -> Unit = { state ->
    if (implicitThrow) {
        state.consumeGas(IMPLICIT_EXCEPTION_THROW_GAS)
    }

    // Throwing exception clears the current stack and pushes its parameter and exit code
    state.stack.clear()
    state.stack.addInt(param)
    with(state.ctx) {
        state.stack.addInt(failure.exitCode.toInt().toBv257())
    }

    val c2 = state.registersOfCurrentContract.c2.value
    if (state.c2IsDefault()) {
        state.setExit(TvmMethodResult.TvmFailure(failure, level, state.phase))
    } else {
        state.newStmt(TsaArtificialJmpToContInst(c2, state.lastStmt.location))
    }
}

fun TvmState.setExit(methodResult: TvmMethodResult) =
    when (phase) {
        COMPUTE_PHASE -> newStmt(TsaArtificialActionPhaseInst(methodResult, lastStmt.location))
        ACTION_PHASE -> newStmt(TsaArtificialExitInst(methodResult, lastStmt.location))
        else -> error("Unexpected exit on phase: $phase")
    }

fun <R> TvmStepScopeManager.calcOnStateCtx(block: context(TvmContext) TvmState.() -> R): R = calcOnState {
    block(ctx, this)
}

fun <R> TvmStepScopeManager.doWithCtx(block: context(TvmContext) TvmStepScopeManager.() -> R): R {
    val ctx = calcOnState { ctx }
    return block(ctx, this)
}

fun TvmStepScopeManager.doWithStateCtx(block: context(TvmContext) TvmState.() -> Unit) = doWithState {
    block(ctx, this)
}

fun TvmState.generateSymbolicCell(): UConcreteHeapRef = generateSymbolicRef(TvmCellType).also { initializeSymbolicCell(it) }

fun TvmState.ensureSymbolicCellInitialized(ref: UHeapRef) =
    ensureSymbolicRefInitialized(ref, TvmCellType) { initializeSymbolicCell(it) }

fun TvmState.generateSymbolicSlice(): UConcreteHeapRef =
    generateSymbolicRef(TvmSliceType).also { initializeSymbolicSlice(it) }

fun TvmState.ensureSymbolicSliceInitialized(ref: UHeapRef) =
    ensureSymbolicRefInitialized(ref, TvmSliceType) { initializeSymbolicSlice(it) }

fun TvmState.initializeSymbolicCell(cell: UConcreteHeapRef) = with(ctx) {
    val dataLength = memory.readField(cell, TvmContext.cellDataLengthField, sizeSort)
    val refsLength = memory.readField(cell, TvmContext.cellRefsLengthField, sizeSort)

    // We can add these constraints manually to path constraints because default values (0) in models are valid
    // for these fields

    pathConstraints += mkSizeLeExpr(dataLength, maxDataLengthSizeExpr)
    pathConstraints += mkSizeGeExpr(dataLength, zeroSizeExpr)

    pathConstraints += mkSizeLeExpr(refsLength, maxRefsLengthSizeExpr)
    pathConstraints += mkSizeGeExpr(refsLength, zeroSizeExpr)
}

fun TvmState.initializeSymbolicSlice(ref: UConcreteHeapRef) = with(ctx) {
    // TODO hack! Assume that all input slices were not read, that means dataPos == 0 and refsPos == 0
    memory.writeField(ref, TvmContext.sliceDataPosField, sizeSort, mkSizeExpr(0), guard = trueExpr)
    memory.writeField(ref, TvmContext.sliceRefPosField, sizeSort, mkSizeExpr(0), guard = trueExpr)

    // Cell in input slices must be represented with static refs to be correctly processed in TvmCellRefsRegion
    val cell = generateSymbolicCell()
    memory.writeField(ref, TvmContext.sliceCellField, addressSort, cell, guard = trueExpr)
    assertType(cell, TvmDataCellType)
}

fun TvmState.generateSymbolicBuilder(): UConcreteHeapRef =
    generateSymbolicRef(TvmBuilderType).also { initializeSymbolicBuilder(it) }

fun TvmState.ensureSymbolicBuilderInitialized(ref: UHeapRef) =
    ensureSymbolicRefInitialized(ref, TvmBuilderType) { initializeSymbolicBuilder(it) }

fun TvmState.initializeSymbolicBuilder(ref: UConcreteHeapRef) = with(ctx) {
//    // TODO hack! Assume that all input builder were not written, that means dataLength == 0 and refsLength == 0
//    memory.writeField(ref, TvmContext.cellDataLengthField, sizeSort, mkSizeExpr(0), guard = trueExpr)
//    memory.writeField(ref, TvmContext.cellRefsLengthField, sizeSort, mkSizeExpr(0), guard = trueExpr)
}

fun TvmStepScopeManager.assertIfSat(
    constraint: UBoolExpr
): Boolean {
    val originalState = calcOnState { this }
    val (stateWithConstraint) = originalState.ctx.statesForkProvider.forkMulti(originalState, listOf(constraint))
    return stateWithConstraint != null
}

fun TvmContext.signedIntegerFitsBits(value: UExpr<TvmInt257Sort>, bits: UInt): UBoolExpr =
    when {
        bits == 0u -> value eq zeroValue
        bits >= TvmContext.INT_BITS -> trueExpr
        else -> mkAnd(
            mkBvSignedLessOrEqualExpr(value, powerOfTwo(bits - 1u).minus(BigInteger.ONE).toBv257()),
            mkBvSignedGreaterOrEqualExpr(value, powerOfTwo(bits - 1u).negate().toBv257()),
        )
    }

/**
 * Since TVM integers have a signed representation only, every non-negative integer fits in 256 bits
 */
fun TvmContext.unsignedIntegerFitsBits(value: UExpr<TvmInt257Sort>, bits: UInt): UBoolExpr =
    when {
        bits == 0u -> value eq zeroValue
        bits >= TvmContext.INT_BITS - 1u -> mkBvSignedGreaterOrEqualExpr(value, zeroValue)
        else -> mkAnd(
            mkBvSignedLessOrEqualExpr(value, maxUnsignedValue(bits).toBv257()),
            mkBvSignedGreaterOrEqualExpr(value, zeroValue),
        )
    }

/**
 * 0 <= [sizeBits] <= 257
 */
fun TvmContext.signedIntegerFitsBits(value: UExpr<TvmInt257Sort>, bits: UExpr<TvmInt257Sort>): UBoolExpr =
    mkAnd(
        mkBvSignedLessOrEqualExpr(bvMinValueSignedExtended(bits), value),
        mkBvSignedLessOrEqualExpr(value, bvMaxValueSignedExtended(bits)),
    )


/**
 * 0 <= [sizeBits] <= 256
 *
 * @see unsignedIntegerFitsBits
 */
fun TvmContext.unsignedIntegerFitsBits(value: UExpr<TvmInt257Sort>, bits: UExpr<TvmInt257Sort>): UBoolExpr =
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
        falseBranch = mkBvNegationExpr(mkBvShiftLeftExpr(one, mkBvSubExpr(sizeBits, one)))
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
        falseBranch = mkBvSubExpr(mkBvShiftLeftExpr(one, mkBvSubExpr(sizeBits, one)), one)
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
    gasUsage.fold(ctx.zeroSizeExpr) { acc, value -> ctx.mkSizeAddExpr(acc, value) }


fun TvmState.assertType(value: UHeapRef, type: TvmType) {
    if (value is UConcreteHeapRef && value.address == NULL_ADDRESS) {
        require(type is TvmNullType)
        return
    }
    val refHandler = { acc: MutableList<Pair<TvmType, UConcreteHeapRef>>, ref: GuardedExpr<UConcreteHeapRef> ->
        val cur = memory.types.getTypeStream(ref.expr)
        require(cur is USingleTypeStream)
        acc += (cur.commonSuperType to ref.expr)
        acc
    }
    val refOldTypes = foldHeapRef(
        ref = value,
        initial = mutableListOf(),
        initialGuard = ctx.trueExpr,
        collapseHeapRefs = false,
        staticIsConcrete = true,
        blockOnConcrete = refHandler,
        blockOnSymbolic =  { _, ref -> error("Unexpected symbolic ref ${ref.expr}") }
    )
    refOldTypes.forEach { (oldType, ref) ->
        if (typeSystem.isSupertype(oldType, type)) {
            memory.types.allocate(ref.address, type)
        } else if (!typeSystem.isSupertype(type, oldType)) {
            // TODO implement guard types
            logger.debug {
                "Type mismatch of $value ($ref) ref. Old type: $oldType, new type: $type"
            }
//            throw TypeCastException(oldType, type)
        }
    }
}

fun TvmStepScopeManager.killCurrentState() = doWithCtx {
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
    allowInputStackValues: Boolean,
): TvmContractExecutionMemory {
    val contractCode = contractsCode[contractId]
    val ctx = state.ctx
    val c4 = state.contractIdToC4Register[contractId]
        ?: error("c4 for contract $contractId is not found")
    val firstElementOfC7 = state.contractIdToFirstElementOfC7[contractId]
        ?: error("First element of c7 for contract $contractId not found")
    return TvmContractExecutionMemory(
        TvmStack(ctx, allowInputValues = allowInputStackValues),
        TvmRegisters(
            ctx,
            C0Register(ctx.quit0Cont),
            C1Register(ctx.quit1Cont),
            C2Register(TvmExceptionContinuation),
            C3Register(TvmOrdContinuation(contractCode.mainMethod)),
            c4,
            C5Register(TvmCellValue(state.allocEmptyCell())),
            C7Register(state.initC7(firstElementOfC7)),
        )
    )
}

fun TvmState.contractEpilogue() {
    contractIdToFirstElementOfC7 = contractIdToFirstElementOfC7.put(
        currentContract,
        registersOfCurrentContract.c7.value[0, stack].cell(stack) as TvmStackTupleValueConcreteNew
    )

    val commitedState = lastCommitedStateOfContracts[currentContract]
        ?: return

    contractIdToC4Register = contractIdToC4Register.put(currentContract, commitedState.c4)
    // last commited state is cleared, as [currentContract] can be visited multiple times
    lastCommitedStateOfContracts = lastCommitedStateOfContracts.remove(currentContract)
}

fun TvmState.switchToFirstMethodInContract(contractCode: TsaContractCode, methodId: MethodId) = with(ctx) {
    if (tvmOptions.useMainMethodForInitialMethodJump) {
        val methodIdAsInt = methodId.toBv257()
        stack.addInt(methodIdAsInt)
        newStmt(contractCode.mainMethod.instList.first())
    } else {
        val method = contractCode.methods[methodId]
            ?: error("Method $methodId not found")
        newStmt(method.instList.first())
    }
}
