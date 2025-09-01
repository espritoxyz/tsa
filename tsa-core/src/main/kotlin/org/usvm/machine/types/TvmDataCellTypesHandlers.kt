package org.usvm.machine.types

import io.ksmt.expr.KBitVecValue
import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KBvSort
import kotlinx.collections.immutable.persistentListOf
import org.ton.Endian
import org.ton.TlbAddressByRef
import org.ton.TlbBitArrayByRef
import org.ton.TlbCoinsLabel
import org.ton.TlbCompositeLabel
import org.ton.TlbIntegerLabelOfConcreteSize
import org.ton.TlbIntegerLabelOfSymbolicSize
import org.ton.TlbLabel
import org.ton.TlbStructure
import org.ton.TvmParameterInfo
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.isAllocated
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.TvmCellDataSort
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.TvmStepScopeManager.ActionOnCondition
import org.usvm.machine.intValue
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmStructuralError
import org.usvm.machine.state.allocCellFromData
import org.usvm.machine.state.calcOnStateCtx
import org.usvm.machine.state.doWithCtx
import org.usvm.machine.state.setExit
import org.usvm.machine.types.memory.ConcreteSizeBlockField
import org.usvm.machine.types.memory.SliceRefField
import org.usvm.machine.types.memory.SymbolicSizeBlockField
import org.usvm.machine.types.memory.stack.LimitedLoadData
import org.usvm.machine.types.memory.stack.TlbStack
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeSubExpr
import org.usvm.sizeSort
import org.usvm.utils.extractAddresses

sealed interface MakeSliceTypeLoadOutcome

private data class NewTlbStack(val stack: TlbStack) : MakeSliceTypeLoadOutcome

private data class Error(val error: TvmStructuralError) : MakeSliceTypeLoadOutcome

private data object NoTlbStack : MakeSliceTypeLoadOutcome

private fun <T> MutableMap<T, UBoolExpr>.addGuard(key: T, guard: UBoolExpr) = with(guard.ctx) {
    val oldValue = this@addGuard[key] ?: falseExpr
    this@addGuard[key] = mkOr(oldValue, guard, flat = false)
}

private fun <T, U> MutableMap<T, MutableMap<U, UBoolExpr>>.addGuard(keyOuter: T, keyInner: U, guard: UBoolExpr) {
    val innerMap = getOrPut(keyOuter) { hashMapOf() }
    innerMap.addGuard(keyInner, guard)
}

fun <ReadResult : TvmCellDataTypeReadValue> TvmStepScopeManager.makeSliceTypeLoad(
    oldSlice: UHeapRef,
    type: TvmCellDataTypeRead<ReadResult>,
    newSlice: UConcreteHeapRef,
    restActions: TvmStepScopeManager.(ReadResult?) -> Unit,
) {
    val turnOnTLBParsingChecks = doWithCtx { tvmOptions.turnOnTLBParsingChecks }
    val performTlbChecksOnAllocatedCells = doWithCtx { tvmOptions.tlbOptions.performTlbChecksOnAllocatedCells }

    val conditionsForFork = mutableListOf<Triple<UBoolExpr, MakeSliceTypeLoadOutcome, ReadResult?>>()

    calcOnStateCtx {
        val outcomes = hashMapOf<MakeSliceTypeLoadOutcome, MutableMap<ReadResult?, UBoolExpr>>()
        val offset = memory.readField(oldSlice, TvmContext.sliceDataPosField, sizeSort)
        val loadList = dataCellLoadedTypeInfo.loadData(this, offset, type, oldSlice)

        loadList.forEach { load ->
            val tlbStack = dataCellInfoStorage.sliceMapper.getTlbStack(load.sliceAddress)
            tlbStack?.step(this, LimitedLoadData.fromLoadData(load))?.forEach { (guard, stepResult, value) ->
                when (stepResult) {
                    is TlbStack.Error -> {
                        val outcome =
                            if (turnOnTLBParsingChecks && (!load.cellAddress.isAllocated || performTlbChecksOnAllocatedCells)) {
                                Error(stepResult.error)
                            } else {
                                NoTlbStack
                            }
                        outcomes.addGuard(outcome, value, guard and load.guard)
                    }

                    is TlbStack.NewStack -> {
                        val outcome = NewTlbStack(stepResult.stack)
                        outcomes.addGuard(outcome, value, guard and load.guard)
                    }
                }
            } ?: run {
                outcomes.addGuard(NoTlbStack, null, load.guard)
            }
        }

        outcomes.entries.forEach { (outcome, valueMap) ->
            val outcomeAndNoValueGuard = valueMap[null] ?: falseExpr
            conditionsForFork.add(Triple(outcomeAndNoValueGuard, outcome, null))

            valueMap.remove(null)
            if (valueMap.isEmpty()) {
                return@forEach
            }

            val values = valueMap.entries.toList()
            val result = values.subList(1, values.size).fold(values.first().key!!) { acc, (value, guard) ->
                mkIte(
                    ctx,
                    guard,
                    trueBranch = value!!,
                    falseBranch = acc,
                )
            }

            val guard = values.fold(falseExpr as UBoolExpr) { acc, (_, guard) -> acc or guard }

            conditionsForFork.add(Triple(guard, outcome, result))
        }
    }

    doWithConditions(
        givenConditionsWithActions = conditionsForFork.map { (guard, outcome, value) ->
            val action = processMakeSliceTypeLoadOutcome(newSlice, outcome)
            ActionOnCondition(
                action = action,
                condition = guard,
                caseIsExceptional = outcome is Error,
                paramForDoForAllBlock = value,
            )
        },
        doForAllBlock = { param ->
            // we execute [restActions] only on states that haven't terminated yet
            restActions(param)
        }
    )
}

private fun processMakeSliceTypeLoadOutcome(
    newSlice: UConcreteHeapRef,
    outcome: MakeSliceTypeLoadOutcome,
): TvmState.() -> Unit =
    when (outcome) {
        is NoTlbStack -> {
            // nothing
            {}
        }

        is Error -> {
            { setExit(outcome.error) }
        }

        is NewTlbStack -> {
            { dataCellInfoStorage.sliceMapper.mapSliceToTlbStack(newSlice, outcome.stack) }
        }
    }

fun TvmStepScopeManager.assertEndOfCell(
    slice: UHeapRef
): Unit? {
    val turnOnTLBParsingChecks = doWithCtx { tvmOptions.turnOnTLBParsingChecks }
    if (!turnOnTLBParsingChecks) {
        return Unit
    }
    return calcOnStateCtx {
        val cellAddress = memory.readField(slice, TvmContext.sliceCellField, addressSort)
        val offset = memory.readField(slice, TvmContext.sliceDataPosField, sizeSort)
        val refNumber = memory.readField(slice, TvmContext.sliceRefPosField, sizeSort)
        val actions = dataCellLoadedTypeInfo.makeEndOfCell(cellAddress, offset, refNumber)
        actions.forEach {
            val noConflictCond = if (it.cellAddress.isAllocated) {
                trueExpr
            } else {
                dataCellInfoStorage.getNoUnexpectedEndOfReadingCondition(this, it)
            }
            fork(
                noConflictCond,
                falseStateIsExceptional = true,
                blockOnFalseState = {
                    setExit(TvmStructuralError(TvmUnexpectedEndOfReading, phase))
                }
            ) ?: return@calcOnStateCtx null
        }
    }
}

fun TvmStepScopeManager.makeSliceRefLoad(
    oldSlice: UHeapRef,
    newSlice: UConcreteHeapRef,
    restActions: TvmStepScopeManager.() -> Unit,
) {
    val turnOnTLBParsingChecks = doWithCtx { tvmOptions.turnOnTLBParsingChecks }
    if (turnOnTLBParsingChecks) {
        calcOnStateCtx {
            val cellAddress = memory.readField(oldSlice, TvmContext.sliceCellField, addressSort)
            val refNumber =
                mkSizeAddExpr(memory.readField(oldSlice, TvmContext.sliceRefPosField, sizeSort), oneSizeExpr)
            val loadList = dataCellLoadedTypeInfo.loadRef(cellAddress, refNumber)
            loadList.forEach { load ->
                val noConflictCond = if (load.cellAddress.isAllocated) {
                    trueExpr
                } else {
                    dataCellInfoStorage.getNoUnexpectedLoadRefCondition(this, load)
                }
                fork(
                    noConflictCond,
                    falseStateIsExceptional = true,
                    blockOnFalseState = {
                        setExit(TvmStructuralError(TvmUnexpectedRefReading, phase))
                    }
                ) ?: return@calcOnStateCtx null
            }
        } ?: return
    }

    // One cell on a concrete address might both have and not have TL-B scheme for different constraints.
    // This is why absence of TL-B stack is a separate situation on which we have to fork.
    // This is why type of the key is [TlbStack?]
    val possibleTlbStacks = mutableMapOf<TlbStack?, UBoolExpr>()

    calcOnStateCtx {
        val concreteSlices = extractAddresses(oldSlice, extractAllocated = true)
        concreteSlices.forEach { (guard, slice) ->
            val stack = dataCellInfoStorage.sliceMapper.getTlbStack(slice)
            possibleTlbStacks.addGuard(stack, guard)
        }
    }

    doWithConditions(
        possibleTlbStacks.map { (stack, guard) ->
            ActionOnCondition(
                action = { stack?.let { dataCellInfoStorage.sliceMapper.mapSliceToTlbStack(newSlice, it) } },
                condition = guard,
                caseIsExceptional = false,
                paramForDoForAllBlock = Unit
            )
        },
        doForAllBlock = {
            restActions()
        }
    )
}

fun TvmStepScopeManager.makeCellToSlice(
    cellAddress: UHeapRef,
    sliceAddress: UConcreteHeapRef,
    restActions: TvmStepScopeManager.() -> Unit
) {
    // One cell on a concrete address might both have and not have TL-B scheme for different constraints.
    // This is why absence of TL-B stack is a separate situation on which we have to fork.
    // This is why type of the key is [TlbStack?]
    val possibleLabels = mutableMapOf<TlbCompositeLabel?, UBoolExpr>()

    calcOnStateCtx {
        val infoVariants = dataCellInfoStorage.getLabelForFreshSlice(cellAddress)
        infoVariants.forEach { (cellInfo, guard) ->
            val label = (cellInfo as? TvmParameterInfo.DataCellInfo)?.dataCellStructure
            possibleLabels.addGuard(label, guard)
        }
    }

    doWithConditions(
        possibleLabels.map { (label, guard) ->
            ActionOnCondition(
                action = {
                    label?.let {
                        dataCellInfoStorage.sliceMapper.allocateInitialSlice(
                            ctx,
                            sliceAddress,
                            label
                        )
                    }
                },
                condition = guard,
                caseIsExceptional = false,
                paramForDoForAllBlock = Unit,
            )
        },
        doForAllBlock = {
            restActions()
        }
    )
}

fun TvmState.copyTlbToNewBuilder(
    oldBuilder: UConcreteHeapRef,
    newBuilder: UConcreteHeapRef,
) {
    val tlbBuilder = dataCellInfoStorage.mapper.getTlbBuilder(oldBuilder)
        ?: return
    dataCellInfoStorage.mapper.addTlbBuilder(newBuilder, tlbBuilder)
}

private fun TvmState.addTlbLabelToBuilder(
    oldBuilder: UConcreteHeapRef,
    newBuilder: UConcreteHeapRef,
    label: TlbLabel,
    initializeTlbField: (TvmState, UConcreteHeapRef, Int) -> Unit,
) {
    val oldTlbBuilder = dataCellInfoStorage.mapper.getTlbBuilder(oldBuilder)
        ?: return
    val newTlbBuilder = oldTlbBuilder.addTlbLabel(label, initializeTlbField)
    dataCellInfoStorage.mapper.addTlbBuilder(newBuilder, newTlbBuilder)
}

private fun TvmState.addTlbConstantToBuilder(
    oldBuilder: UConcreteHeapRef,
    newBuilder: UConcreteHeapRef,
    constant: String,
) {
    val oldTlbBuilder = dataCellInfoStorage.mapper.getTlbBuilder(oldBuilder)
        ?: return
    val newTlbBuilder = oldTlbBuilder.addConstant(ctx, constant)
    dataCellInfoStorage.mapper.addTlbBuilder(newBuilder, newTlbBuilder)
}

fun TvmState.storeIntTlbLabelToBuilder(
    oldBuilder: UConcreteHeapRef,
    newBuilder: UConcreteHeapRef,
    sizeBits: UExpr<TvmSizeSort>,
    value: UExpr<TvmContext.TvmInt257Sort>,
    isSigned: Boolean,
    endian: Endian,
) = with(ctx) {

    // special case for storing constants
    if (value is KBitVecValue && sizeBits is KInterpretedValue) {
        val constValue = (value as KBitVecValue<*>).stringValue.takeLast(sizeBits.intValue())
        addTlbConstantToBuilder(oldBuilder, newBuilder, constValue)
        return
    }

    if (sizeBits is KInterpretedValue) {

        val bitSizeConcrete = sizeBits.intValue()
        val valueShrinked = value.extractToSort(mkBvSort(bitSizeConcrete.toUInt()))

        val label = TlbIntegerLabelOfConcreteSize(bitSizeConcrete, isSigned = isSigned, endian = endian)

        addTlbLabelToBuilder(oldBuilder, newBuilder, label) { state, ref, structId ->
            val field = ConcreteSizeBlockField(bitSizeConcrete, structId, persistentListOf())
            state.memory.writeField(ref, field, field.getSort(this), valueShrinked, guard = trueExpr)
        }

    } else {

        val label = TlbIntegerLabelOfSymbolicSize(isSigned, endian, arity = 0) { _, _ -> sizeBits }
        val valueShrinked = value.extractToSort(mkBvSort(label.lengthUpperBound.toUInt()))

        addTlbLabelToBuilder(oldBuilder, newBuilder, label) { state, ref, structId ->
            val field = SymbolicSizeBlockField(label.lengthUpperBound, structId, persistentListOf())
            state.memory.writeField(ref, field, field.getSort(this), valueShrinked, guard = trueExpr)
        }
    }
}

fun TvmState.storeCoinTlbLabelToBuilder(
    oldBuilder: UConcreteHeapRef,
    newBuilder: UConcreteHeapRef,
    length: UExpr<KBvSort>,
    value: UExpr<TvmContext.TvmInt257Sort>
) = with(ctx) {
    addTlbLabelToBuilder(oldBuilder, newBuilder, TlbCoinsLabel) { state, ref, structId ->
        val lengthStructure = TlbCoinsLabel.internalStructure as TlbStructure.KnownTypePrefix
        check(lengthStructure.typeLabel is TlbIntegerLabelOfConcreteSize)

        val valueStructure = lengthStructure.rest as TlbStructure.KnownTypePrefix
        check(valueStructure.typeLabel is TlbIntegerLabelOfSymbolicSize)

        val lengthField = ConcreteSizeBlockField(
            lengthStructure.typeLabel.concreteSize,
            lengthStructure.id,
            persistentListOf(structId)
        )
        val lengthSort = lengthField.getSort(this)
        check(lengthSort.sizeBits == length.sort.sizeBits)

        val valueField = SymbolicSizeBlockField(
            valueStructure.typeLabel.lengthUpperBound,
            valueStructure.id,
            persistentListOf(structId)
        )
        val valueSort = valueField.getSort(this)

        val valueShrinked = mkBvExtractExpr(high = valueSort.sizeBits.toInt() - 1, low = 0, value)

        state.memory.writeField(ref, lengthField, lengthSort, length, guard = trueExpr)
        state.memory.writeField(ref, valueField, valueSort, valueShrinked, guard = trueExpr)
    }
}

fun TvmStepScopeManager.storeSliceTlbLabelInBuilder(
    oldBuilder: UConcreteHeapRef,
    newBuilder: UConcreteHeapRef,
    slice: UHeapRef,
) = doWithCtx {
    val cellRef = calcOnState { memory.readField(slice, TvmContext.sliceCellField, addressSort) }
    val dataPos = calcOnState { memory.readField(slice, TvmContext.sliceDataPosField, sizeSort) }
    val cellLength = calcOnState { memory.readField(cellRef, TvmContext.cellDataLengthField, sizeSort) }
    val sliceLength = mkSizeSubExpr(cellLength, dataPos)

    val leafAddresses = extractAddresses(slice, extractAllocated = true, extractStatic = true)

    val (label, resultSliceRef) = if (calcOnState { leafAddresses.all { dataCellInfoStorage.mapper.sliceIsAddress(it.second) } }) {
        // store TL-B address
        TlbAddressByRef(sliceLength) to slice
    } else {

        val newSlice = calcOnState { memory.allocConcrete(TvmSliceType) }
        doWithState {
            memory.writeField(newSlice, TvmContext.sliceCellField, addressSort, cellRef, guard = trueExpr)
            val refsInCell = memory.readField(cellRef, TvmContext.cellRefsLengthField, sizeSort)
            memory.writeField(newSlice, TvmContext.sliceRefPosField, sizeSort, refsInCell, guard = trueExpr)
            memory.writeField(newSlice, TvmContext.sliceDataPosField, sizeSort, dataPos, guard = trueExpr)
        }

        TlbBitArrayByRef(sliceLength) to newSlice
    }

    calcOnState {
        addTlbLabelToBuilder(oldBuilder, newBuilder, label) { state, resultCellRef, structId ->
            val field = SliceRefField(structId, persistentListOf())
            state.memory.writeField(resultCellRef, field, field.getSort(ctx), resultSliceRef, guard = trueExpr)
        }
    }
}

fun TvmStepScopeManager.storeCellDataTlbLabelInBuilder(
    oldBuilder: UConcreteHeapRef,
    newBuilder: UConcreteHeapRef,
    value: UExpr<TvmCellDataSort>,
    sizeBits: UExpr<TvmSizeSort>,
) = doWithCtx {
    val cellRef = allocCellFromData(value, sizeBits)
    val newSlice = calcOnState { memory.allocConcrete(TvmSliceType) }
    doWithState {
        memory.writeField(newSlice, TvmContext.sliceCellField, addressSort, cellRef, guard = trueExpr)
        val refsInCell = memory.readField(cellRef, TvmContext.cellRefsLengthField, sizeSort)
        memory.writeField(newSlice, TvmContext.sliceRefPosField, sizeSort, refsInCell, guard = trueExpr)
    }
    storeSliceTlbLabelInBuilder(oldBuilder, newBuilder, newSlice)
}
