package org.usvm.machine.state

import io.ksmt.KContext
import io.ksmt.expr.KBitVecValue
import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KBvSort
import io.ksmt.utils.uncheckedCast
import org.ton.Endian
import org.ton.bytecode.TvmCell
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.USort
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.isFalse
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.ADDRESS_BITS
import org.usvm.machine.TvmContext.Companion.ADDRESS_TAG_BITS
import org.usvm.machine.TvmContext.Companion.ADDRESS_TAG_LENGTH
import org.usvm.machine.TvmContext.Companion.CELL_DATA_BITS
import org.usvm.machine.TvmContext.Companion.EXTERN_ADDRESS_TAG
import org.usvm.machine.TvmContext.Companion.GRAMS_LENGTH_BITS
import org.usvm.machine.TvmContext.Companion.MAX_DATA_LENGTH
import org.usvm.machine.TvmContext.Companion.NONE_ADDRESS_TAG
import org.usvm.machine.TvmContext.Companion.STD_ADDRESS_TAG
import org.usvm.machine.TvmContext.Companion.STD_WORKCHAIN_BITS
import org.usvm.machine.TvmContext.Companion.VAR_ADDRESS_TAG
import org.usvm.machine.TvmContext.Companion.cellRefsLengthField
import org.usvm.machine.TvmContext.Companion.sliceCellField
import org.usvm.machine.TvmContext.Companion.sliceDataPosField
import org.usvm.machine.TvmContext.Companion.sliceRefPosField
import org.usvm.machine.TvmContext.TvmCellDataSort
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.intValue
import org.usvm.machine.types.TlbStructureBuilder
import org.usvm.machine.types.TvmBuilderType
import org.usvm.machine.types.TvmCellDataCoinsRead
import org.usvm.machine.types.TvmCellDataIntegerRead
import org.usvm.machine.types.TvmCellDataMsgAddrRead
import org.usvm.machine.types.TvmDataCellType
import org.usvm.machine.types.TvmSliceType
import org.usvm.machine.types.makeSliceRefLoad
import org.usvm.machine.types.makeSliceTypeLoad
import org.usvm.machine.types.storeCellDataTlbLabelInBuilder
import org.usvm.machine.types.storeCoinTlbLabelToBuilder
import org.usvm.machine.types.storeIntTlbLabelToBuilder
import org.usvm.machine.types.storeSliceTlbLabelInBuilder
import org.usvm.memory.UWritableMemory
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGeExpr
import org.usvm.mkSizeLeExpr
import org.usvm.mkSizeSubExpr
import org.usvm.sizeSort
import org.usvm.utils.intValueOrNull

private data class GuardedExpr(
    val expr: UExpr<TvmSizeSort>,
    val guard: UBoolExpr,
)

/**
 * Split sizeExpr that represents ite into two ite's:
 * first one has concrete leaves, second one has symbolic leaves.
 */
private fun splitSizeExpr(sizeExpr: UExpr<TvmSizeSort>): Pair<GuardedExpr?, GuardedExpr?> { // (concrete, symbolic)

    /**
     * Merge split ite leaves into one ite.
     * Pair (trueValue, falseValue) is either
     * (trueConcrete, falseConcrete) or (trueSymbolic, falseSymbolic).
     */
    fun KContext.mergeCellExprsIntoIte(
        cond: UBoolExpr,
        trueValue: GuardedExpr?,
        falseValue: GuardedExpr?,
    ): GuardedExpr? =
        when {
            trueValue == null && falseValue == null -> {
                null
            }

            trueValue == null && falseValue != null -> {
                GuardedExpr(falseValue.expr, falseValue.guard and cond.not())
            }

            trueValue != null && falseValue == null -> {
                GuardedExpr(trueValue.expr, trueValue.guard and cond)
            }

            trueValue != null && falseValue != null -> {
                GuardedExpr(
                    mkIte(cond, trueValue.expr, falseValue.expr),
                    (cond and trueValue.guard) or (cond.not() and falseValue.guard)
                )
            }

            else -> {
                error("not reachable")
            }
        }

    val ctx = sizeExpr.ctx
    return with(ctx) {
        when (sizeExpr) {
            is KInterpretedValue ->
                GuardedExpr(sizeExpr, ctx.trueExpr) to null

            is UIteExpr<TvmSizeSort> -> {
                val cond = sizeExpr.condition
                val (trueConcrete, trueSymbolic) = splitSizeExpr(sizeExpr.trueBranch)
                val (falseConcrete, falseSymbolic) = splitSizeExpr(sizeExpr.falseBranch)
                val concrete = mergeCellExprsIntoIte(cond, trueConcrete, falseConcrete)
                val symbolic = mergeCellExprsIntoIte(cond, trueSymbolic, falseSymbolic)
                concrete to symbolic
            }

            else -> {
                // Any complex expressions containing symbolic values are considered fully symbolic
                null to GuardedExpr(sizeExpr, ctx.trueExpr)
            }
        }
    }
}

/**
 * This is function is used to set cellUnderflow error and to set its type
 * (StructuralError, SymbolicStructuralError, RealError or Unknown).
 */
private fun TvmContext.processCellUnderflowCheck(
    size: UExpr<TvmSizeSort>,
    scope: TvmStepScopeManager,
    minSize: UExpr<TvmSizeSort>? = null,
    maxSize: UExpr<TvmSizeSort>? = null,
    quietBlock: (TvmState.() -> Unit)? = null,
): Unit? {
    val noUnderflowExpr =
        scope.calcOnStateCtx {
            val min = minSize?.let { mkSizeGeExpr(size, minSize) } ?: trueExpr
            val max = maxSize?.let { mkSizeLeExpr(size, maxSize) } ?: trueExpr
            min and max
        }

    // don't bother with concrete and symbolic cases if so
    if (!scope.allowFailuresOnCurrentStep || quietBlock != null) {
        return scope.fork(
            noUnderflowExpr,
            falseStateIsExceptional = quietBlock == null,
            blockOnFalseState = {
                quietBlock?.invoke(this)
                    ?: throwUnknownCellUnderflowError(this)
            }
        )
    }

    // cases for concrete and symbolic sizes are different:
    // this is why we need to split `size` if it represents ite.
    val (concreteSize, symbolicSize) = splitSizeExpr(size)
    val concreteGuard = concreteSize?.guard ?: falseExpr
    val symbolicGuard = symbolicSize?.guard ?: falseExpr

    // Case of concrete size: cellUnderflow is always a real error.
    scope.fork(
        concreteGuard implies noUnderflowExpr,
        falseStateIsExceptional = true,
        blockOnFalseState = {
            throwRealCellUnderflowError(this)
        }
    ) ?: return null

    // Case of symbolic size.
    // First we distinguish StructuralError and SymbolicStructuralError.
    val isConcreteBound = (minSize is KInterpretedValue?) && (maxSize is KInterpretedValue?)
    var symbolicThrow =
        if (isConcreteBound) {
            throwStructuralCellUnderflowError
        } else {
            throwSymbolicStructuralCellUnderflowError
        }
    // Here cellUnderflow can be either structural, real or unknown.
    // It is structural error if state without cellUnderflow is possible.
    // It is real error if state without cellUnderflow is UNSTAT.
    // If solver returned UNKNOWN, the type of cellUnderflow is unknown.
    return scope.forkWithCheckerStatusKnowledge(
        symbolicGuard implies noUnderflowExpr,
        blockOnUnknownTrueState = { symbolicThrow = throwUnknownCellUnderflowError },
        blockOnUnsatTrueState = { symbolicThrow = throwRealCellUnderflowError },
        blockOnFalseState = {
            symbolicThrow(this)
        }
    )
}

fun TvmContext.checkCellDataUnderflow(
    scope: TvmStepScopeManager,
    cellRef: UHeapRef,
    minSize: UExpr<TvmSizeSort>? = null,
    maxSize: UExpr<TvmSizeSort>? = null,
    quietBlock: (TvmState.() -> Unit)? = null,
): Unit? {
    val cellSize =
        scope.calcOnStateCtx {
            fieldManagers.cellDataLengthFieldManager.readCellDataLength(this, cellRef)
        }
    return processCellUnderflowCheck(cellSize, scope, minSize, maxSize, quietBlock)
}

fun TvmContext.checkCellRefsUnderflow(
    scope: TvmStepScopeManager,
    cellRef: UHeapRef,
    minSize: UExpr<TvmSizeSort>? = null,
    maxSize: UExpr<TvmSizeSort>? = null,
    quietBlock: (TvmState.() -> Unit)? = null,
): Unit? {
    val cellSize = scope.calcOnStateCtx { memory.readField(cellRef, cellRefsLengthField, sizeSort) }
    return processCellUnderflowCheck(cellSize, scope, minSize, maxSize, quietBlock)
}

fun checkCellOverflow(
    noOverflowExpr: UBoolExpr,
    scope: TvmStepScopeManager,
    quietBlock: (TvmState.() -> Unit)? = null,
): Unit? =
    scope.fork(
        noOverflowExpr,
        falseStateIsExceptional = (quietBlock == null),
        blockOnFalseState = {
            quietBlock?.invoke(this)
                ?: ctx.throwCellOverflowError(this)
        }
    )

fun setBuilderLengthOrThrowCellOverflow(
    scope: TvmStepScopeManager,
    oldBuilder: UHeapRef,
    newBuilder: UConcreteHeapRef,
    writeSizeBits: UExpr<TvmSizeSort>,
    writeSizeBitsUpperBound: Int?,
    doNotUpdateLength: Boolean = false,
    quietBlock: (TvmState.() -> Unit)? = null,
): Unit? =
    with(scope.ctx) {
        val oldBuilderUpperBound =
            scope.calcOnState {
                fieldManagers.cellDataLengthFieldManager.getUpperBound(this@with, oldBuilder)
            }

        val newUpperBound =
            if (oldBuilderUpperBound != null && writeSizeBitsUpperBound != null) {
                (oldBuilderUpperBound + writeSizeBitsUpperBound).takeIf { it <= MAX_DATA_LENGTH }
            } else {
                null
            }

        val newLength =
            scope.calcOnState {
                mkBvAddExpr(
                    fieldManagers.cellDataLengthFieldManager.readCellDataLength(this, oldBuilder),
                    writeSizeBits
                )
            }

        if (newUpperBound == null) {
            checkCellOverflow(
                mkBvSignedLessOrEqualExpr(newLength, mkSizeExpr(MAX_DATA_LENGTH)),
                scope,
                quietBlock
            ) ?: return@with null
        }

        if (!doNotUpdateLength) {
            scope.calcOnState {
                fieldManagers.cellDataLengthFieldManager.writeCellDataLength(this, newBuilder, newLength, newUpperBound)
            }
        }

        return Unit
    }

fun TvmStepScopeManager.assertDataLengthConstraintWithoutError(
    cellDataLength: UExpr<TvmSizeSort>,
    unsatBlock: TvmState.() -> Unit,
): Unit? =
    calcOnStateCtx {
        val correctnessConstraint =
            mkAnd(
                mkSizeLeExpr(zeroSizeExpr, cellDataLength),
                mkSizeLeExpr(cellDataLength, maxDataLengthSizeExpr)
            )
        assert(correctnessConstraint, unsatBlock = unsatBlock)
    }

fun TvmStepScopeManager.assertRefsLengthConstraintWithoutError(
    cellRefsLength: UExpr<TvmSizeSort>,
    unsatBlock: TvmState.() -> Unit,
): Unit? =
    calcOnStateCtx {
        val correctnessConstraint =
            mkAnd(
                mkSizeLeExpr(zeroSizeExpr, cellRefsLength),
                mkSizeLeExpr(cellRefsLength, maxRefsLengthSizeExpr)
            )
        assert(correctnessConstraint, unsatBlock = unsatBlock)
    }

fun TvmStepScopeManager.preloadDataBitsFromCellWithoutChecks(
    cell: UHeapRef,
    offset: UExpr<TvmSizeSort>,
    sizeBits: UExpr<TvmSizeSort>,
): UExpr<TvmCellDataSort>? {
    val cellDataFieldManager =
        calcOnState {
            fieldManagers.cellDataFieldManager
        }
    val cellData =
        cellDataFieldManager.readCellData(this@preloadDataBitsFromCellWithoutChecks, cell)
            ?: return null
    return calcOnStateCtx {
        val endOffset = mkSizeAddExpr(offset, sizeBits)
        val offsetDataPos = mkSizeSubExpr(maxDataLengthSizeExpr, endOffset)
        mkBvLogicalShiftRightExpr(cellData, offsetDataPos.zeroExtendToSort(cellDataSort))
    }
}

fun TvmStepScopeManager.preloadDataBitsFromCellWithoutChecks(
    cell: UHeapRef,
    offset: UExpr<TvmSizeSort>,
    sizeBits: Int,
): UExpr<KBvSort>? =
    doWithCtx {
        val shiftedData =
            preloadDataBitsFromCellWithoutChecks(cell, offset, mkSizeExpr(sizeBits))
                ?: return@doWithCtx null
        return@doWithCtx mkBvExtractExpr(high = sizeBits - 1, low = 0, shiftedData)
    }

fun TvmStepScopeManager.slicePreloadDataBitsWithoutChecks(
    slice: UHeapRef,
    sizeBits: Int,
): UExpr<KBvSort>? {
    val cell =
        calcOnStateCtx {
            memory.readField(slice, sliceCellField, addressSort)
        }
    val dataPosition =
        calcOnStateCtx {
            memory.readField(slice, sliceDataPosField, sizeSort)
        }
    return preloadDataBitsFromCellWithoutChecks(cell, dataPosition, sizeBits)
}

/**
 * @return bv 1023 with undefined high-order bits
 */
fun TvmStepScopeManager.slicePreloadDataBits(
    slice: UHeapRef,
    sizeBits: UExpr<TvmSizeSort>,
    quietBlock: (TvmState.() -> Unit)? = null,
): UExpr<TvmCellDataSort>? =
    calcOnStateCtx {
        val cell = memory.readField(slice, sliceCellField, addressSort)
        val cellDataLength = fieldManagers.cellDataLengthFieldManager.readCellDataLength(this, cell)

        assertDataLengthConstraintWithoutError(
            cellDataLength,
            unsatBlock = { error("Cannot ensure correctness for data length in cell $cell") }
        ) ?: return@calcOnStateCtx null

        val dataPosition = memory.readField(slice, sliceDataPosField, sizeSort)
        val readingEnd = mkBvAddExpr(dataPosition, sizeBits)

        checkCellDataUnderflow(this@slicePreloadDataBits, cell, minSize = readingEnd, quietBlock = quietBlock)
            ?: return@calcOnStateCtx null

        preloadDataBitsFromCellWithoutChecks(cell, dataPosition, sizeBits)
    }

fun TvmStepScopeManager.slicePreloadDataBits(
    slice: UHeapRef,
    bits: Int,
    quietBlock: (TvmState.() -> Unit)? = null,
): UExpr<UBvSort>? =
    doWithCtx {
        val data =
            slicePreloadDataBits(slice, mkSizeExpr(bits), quietBlock)
                ?: return@doWithCtx null

        mkBvExtractExpr(high = bits - 1, low = 0, data)
    }

fun TvmContext.extractIntFromShiftedData(
    shiftedData: UExpr<TvmCellDataSort>,
    sizeBits: UExpr<TvmInt257Sort>,
    isSigned: Boolean,
): UExpr<TvmInt257Sort> {
    val extractedBits = shiftedData.extractToInt257Sort()
    val trashBits = mkBvSubExpr(intBitsValue, sizeBits)
    val shiftedBits = mkBvShiftLeftExpr(extractedBits, trashBits)

    return if (!isSigned) {
        mkBvLogicalShiftRightExpr(shiftedBits, trashBits)
    } else {
        mkBvArithShiftRightExpr(shiftedBits, trashBits)
    }
}

/**
 * 0 <= bits <= 257
 */
fun TvmStepScopeManager.slicePreloadInt(
    slice: UHeapRef,
    sizeBits: UExpr<TvmInt257Sort>,
    isSigned: Boolean,
    quietBlock: (TvmState.() -> Unit)? = null,
): UExpr<TvmInt257Sort>? {
    val shiftedData =
        calcOnStateCtx { slicePreloadDataBits(slice, sizeBits.extractToSizeSort(), quietBlock) }
            ?: return null

    return calcOnStateCtx {
        extractIntFromShiftedData(shiftedData, sizeBits, isSigned)
    }
}

private fun TvmStepScopeManager.slicePreloadInternalAddrLengthConstraint(
    slice: UHeapRef,
): Pair<UBoolExpr, UExpr<TvmSizeSort>>? =
    doWithCtx {
        // addr_var$11 anycast:(Maybe Anycast) addr_len:(## 9) workchain_id:int32
        // addr_std$10 anycast:(Maybe Anycast) workchain_id:int8 ...
        val prefixLen = 44
        val data =
            slicePreloadDataBitsWithoutChecks(slice, sizeBits = prefixLen)
                ?: return@doWithCtx null

        // addr_std$10
        // addr_var$11
        val tag = mkBvExtractExpr(high = prefixLen - 1, low = prefixLen - 2, data)

        // anycast:(Maybe Anycast)
        // since TVM 10, must be 0. We set TvmUsageOfAnycastAddress if it is 1
        val anycastBit = mkBvExtractExpr(high = prefixLen - 3, low = prefixLen - 3, data)
        val noAnycastConstraint = anycastBit eq zeroBit

        // addr_std$10
        val stdConstraint = tag eq mkBv(STD_ADDRESS_TAG, ADDRESS_TAG_BITS)
        // workchain_id:int8
        val stdWorkchain = mkBvExtractExpr(high = prefixLen - 4, low = prefixLen - 11, data).signedExtendToInteger()
        val stdWorkchainConstraint = (stdWorkchain eq baseChain) or (stdWorkchain eq masterchain)
        val stdLength = mkSizeExpr(ADDRESS_TAG_LENGTH + 1 + STD_WORKCHAIN_BITS + ADDRESS_BITS)

        // addr_var$11
        // since TVM 10, forbidden. We set TvmUsageOfVarAddress if it is used
        val varConstraint = tag eq mkBv(VAR_ADDRESS_TAG, ADDRESS_TAG_BITS)

        val noAnycastIfInternal = stdConstraint implies noAnycastConstraint

        val constraintAndFailureList =
            listOf(
                noAnycastIfInternal to TvmUsageOfAnycastAddress,
                varConstraint.not() to TvmUsageOfVarAddress
            )

        for ((assumeConstraint, failure) in constraintAndFailureList) {
            if (calcOnState { models.all { it.eval(assumeConstraint).isFalse } }) {
                var falseState: TvmState? = null
                fork(
                    assumeConstraint,
                    falseStateIsExceptional = true,
                    blockOnFalseState = {
                        falseState = this
                        setExit(TvmMethodResult.TvmSoftFailure(failure, phase))
                    }
                ) ?: return@doWithCtx null

                // if reached this, then we found state with [assumeConstraint], and we can get rid of [falseState]
                // TODO: maybe keep false state only if true state is unsat?
                falseState?.let { killForkedState(it) }
            } else {
                assert(
                    assumeConstraint,
                    unsatBlock = {
                        error("Must not be reachable")
                    },
                    unknownBlock = {
                        error("Must not be reachable")
                    }
                ) ?: return@doWithCtx null
            }
        }

        assert(
            mkAnd(
                stdConstraint implies stdWorkchainConstraint
            )
        ) ?: return@doWithCtx null

        stdConstraint to stdLength
    }

private fun TvmStepScopeManager.slicePreloadExternalAddrLengthConstraint(
    slice: UHeapRef,
    mustProcessAllAddressFormats: Boolean,
): Pair<UBoolExpr, UExpr<TvmSizeSort>>? =
    doWithCtx {
        if (!tvmOptions.enableExternalAddress && !mustProcessAllAddressFormats) {
            return@doWithCtx falseExpr to zeroSizeExpr
        }
        // addr_extern$01 len:(## 9)
        // addr_none$00 ...
        val prefixLen = 11
        val data =
            slicePreloadDataBitsWithoutChecks(slice, sizeBits = prefixLen)
                ?: return@doWithCtx null

        val tag = mkBvExtractExpr(high = prefixLen - 1, low = prefixLen - 2, data)

        // addr_none$00
        val noneConstraint = tag eq mkBv(NONE_ADDRESS_TAG, ADDRESS_TAG_BITS)
        val noneLength = mkSizeExpr(ADDRESS_TAG_LENGTH)

        // addr_extern$01
        val externConstraint = tag eq mkBv(EXTERN_ADDRESS_TAG, ADDRESS_TAG_BITS)
        // len:(## 9)
        val externAddrLength =
            mkBvExtractExpr(
                high = prefixLen - 3,
                low = prefixLen - 11,
                data
            ).zeroExtendToSort(sizeSort)
        val externLength = mkSizeAddExpr(mkSizeExpr(ADDRESS_TAG_LENGTH + 9), externAddrLength)

        val addrLength =
            mkIte(
                noneConstraint,
                noneLength,
                externLength
            )

        (noneConstraint or externConstraint) to addrLength
    }

fun TvmStepScopeManager.slicePreloadInternalAddrLength(slice: UHeapRef): UExpr<TvmSizeSort>? {
    val (constraint, length) =
        slicePreloadInternalAddrLengthConstraint(slice)
            ?: return null

    fork(
        constraint,
        falseStateIsExceptional = true,
        blockOnFalseState = {
            // TODO tl-b parsing failure
            ctx.throwUnknownCellUnderflowError(this)
        }
    ) ?: return null

    return length
}

fun TvmStepScopeManager.slicePreloadExternalAddrLength(
    slice: UHeapRef,
    mustProcessAllAddressFormats: Boolean = false,
): UExpr<TvmSizeSort>? {
    val (constraint, length) =
        slicePreloadExternalAddrLengthConstraint(slice, mustProcessAllAddressFormats)
            ?: return null

    fork(
        constraint,
        falseStateIsExceptional = true,
        blockOnFalseState = {
            // TODO tl-b parsing failure
            ctx.throwUnknownCellUnderflowError(this)
        }
    ) ?: return null

    return length
}

fun TvmStepScopeManager.slicePreloadAddrLengthWithoutSetException(
    slice: UHeapRef,
    mustProcessAllAddressFormats: Boolean = false,
): UExpr<TvmSizeSort>? =
    calcOnStateCtx {
        val (intConstraint, intLength) =
            slicePreloadInternalAddrLengthConstraint(slice)
                ?: return@calcOnStateCtx null
        val (extConstraint, extLength) =
            slicePreloadExternalAddrLengthConstraint(slice, mustProcessAllAddressFormats)
                ?: return@calcOnStateCtx null

        assert(intConstraint or extConstraint)
            ?: return@calcOnStateCtx null

        val length =
            mkIte(
                extConstraint,
                extLength,
                intLength
            )

        length
    }

fun sliceLoadGramsTlb(
    scope: TvmStepScopeManager,
    oldSlice: UHeapRef,
    newSlice: UConcreteHeapRef,
    doWithGrams: TvmStepScopeManager.(UExpr<TvmInt257Sort>) -> Unit,
) = scope.doWithCtx {
    val ctx = scope.calcOnState { ctx }

    val read = TvmCellDataCoinsRead(ctx)
    scope.makeSliceTypeLoad(oldSlice, read, newSlice) { valueFromTlb ->

        val (length, grams) =
            valueFromTlb?.let {
                doWithState {
                    sliceMoveDataPtr(newSlice, bits = 4)
                }

                val length = valueFromTlb.first.zeroExtendToSort(sizeSort)

                val grams = valueFromTlb.second

                length to grams
            } ?: run {
                val length =
                    slicePreloadDataBits(newSlice, bits = 4)?.zeroExtendToSort(sizeSort)
                        ?: return@makeSliceTypeLoad

                doWithState {
                    sliceMoveDataPtr(newSlice, bits = 4)
                }

                val extendedLength = mkBvShiftLeftExpr(length, shift = threeSizeExpr)
                val grams =
                    slicePreloadInt(newSlice, extendedLength.zeroExtendToSort(int257sort), isSigned = false)
                        ?: return@makeSliceTypeLoad

                length to grams
            }

        val extendedLength = mkBvShiftLeftExpr(length, shift = threeSizeExpr)

        doWithState {
            sliceMoveDataPtr(newSlice, extendedLength)
        }

        doWithGrams(grams)
    }
}

fun TvmStepScopeManager.slicePreloadRef(
    slice: UHeapRef,
    idx: UExpr<TvmSizeSort>,
    quietBlock: (TvmState.() -> Unit)? = null,
): UHeapRef? =
    calcOnStateCtx {
        val cell = memory.readField(slice, sliceCellField, addressSort)
        val refsLength = memory.readField(cell, cellRefsLengthField, sizeSort)

        assertRefsLengthConstraintWithoutError(
            refsLength,
            unsatBlock = { error("Cannot ensure correctness for number of refs in cell $cell") }
        ) ?: return@calcOnStateCtx null

        val sliceRefPos = memory.readField(slice, sliceRefPosField, sizeSort)
        val refIdx = mkSizeAddExpr(sliceRefPos, idx)

        val minSize = mkBvAddExpr(refIdx, mkBv(1))
        checkCellRefsUnderflow(this@slicePreloadRef, cell, minSize = minSize, quietBlock = quietBlock)
            ?: return@calcOnStateCtx null

        readCellRef(cell, refIdx)
    }

fun TvmStepScopeManager.slicePreloadNextRef(
    slice: UHeapRef,
    quietBlock: (TvmState.() -> Unit)? = null,
): UHeapRef? = calcOnStateCtx { slicePreloadRef(slice, zeroSizeExpr, quietBlock) }

fun TvmState.sliceCopy(
    original: UHeapRef,
    result: UHeapRef,
) = with(ctx) {
    memory.copyField(original, result, sliceCellField, addressSort)
    memory.copyField(original, result, sliceDataPosField, sizeSort)
    memory.copyField(original, result, sliceRefPosField, sizeSort)
}

fun TvmStepScopeManager.sliceDeepCopy(
    original: UHeapRef,
    result: UHeapRef,
): Unit? =
    doWithCtx {
        val cell =
            calcOnState {
                memory.readField(original, sliceCellField, addressSort)
            }
        val cellCopy =
            calcOnState {
                memory.allocConcrete(TvmDataCellType)
            }.also {
                builderCopy(cell, it)
                    ?: return@doWithCtx null
            }

        doWithState {
            memory.writeField(result, sliceCellField, addressSort, cellCopy, guard = trueExpr)
            memory.copyField(original, result, sliceDataPosField, sizeSort)
            memory.copyField(original, result, sliceRefPosField, sizeSort)
        }
    }

fun TvmState.sliceMoveDataPtr(
    slice: UHeapRef,
    bits: UExpr<TvmSizeSort>,
) = with(ctx) {
    val dataPosition = memory.readField(slice, sliceDataPosField, sizeSort)
    val updatedDataPosition = mkSizeAddExpr(dataPosition, bits)
    memory.writeField(slice, sliceDataPosField, sizeSort, updatedDataPosition, guard = trueExpr)
}

fun TvmState.sliceMoveDataPtr(
    slice: UHeapRef,
    bits: Int,
) = with(ctx) {
    sliceMoveDataPtr(slice, mkSizeExpr(bits))
}

fun TvmState.sliceMoveRefPtr(
    slice: UHeapRef,
    shift: UExpr<TvmSizeSort> = ctx.mkSizeExpr(1),
) = with(ctx) {
    val refPosition = memory.readField(slice, sliceRefPosField, sizeSort)
    val updatedRefPosition = mkSizeAddExpr(refPosition, shift)
    memory.writeField(slice, sliceRefPosField, sizeSort, updatedRefPosition, guard = trueExpr)
}

fun TvmState.allocEmptyBuilder(): UConcreteHeapRef =
    memory.allocConcrete(TvmBuilderType).also {
        builderCopyFromBuilder(emptyRefValue.emptyBuilder, it)
        dataCellInfoStorage.mapper.addTlbBuilder(it, TlbStructureBuilder.empty)
    }

fun TvmState.builderCopyFromBuilder(
    original: UConcreteHeapRef,
    result: UConcreteHeapRef,
) = with(ctx) {
    val cellData =
        fieldManagers.cellDataFieldManager.readCellDataForBuilderOrAllocatedCell(this@builderCopyFromBuilder, original)
    fieldManagers.cellDataFieldManager.writeCellData(this@builderCopyFromBuilder, result, cellData)
    fieldManagers.cellDataLengthFieldManager.copyLength(this@builderCopyFromBuilder, original, result)
    memory.copyField(original, result, cellRefsLengthField, sizeSort)
    copyCellRefs(original, result)
}

fun TvmStepScopeManager.builderCopy(
    original: UHeapRef,
    result: UConcreteHeapRef,
): Unit? =
    doWithCtx {
        val cellDataFieldManager = calcOnState { fieldManagers.cellDataFieldManager }
        val cellData =
            cellDataFieldManager.readCellData(this, original)
                ?: return@doWithCtx null
        doWithState {
            cellDataFieldManager.writeCellData(this, result, cellData)
            fieldManagers.cellDataLengthFieldManager.copyLength(this, from = original, to = result)
            memory.copyField(original, result, cellRefsLengthField, sizeSort)
            copyCellRefs(original, result)
        }
    }

private fun TvmState.builderStoreDataBitsNoOverflowCheck(
    builder: UConcreteHeapRef,
    bits: UExpr<UBvSort>,
) = with(ctx) {
    val builderData =
        fieldManagers.cellDataFieldManager.readCellDataForBuilderOrAllocatedCell(
            this@builderStoreDataBitsNoOverflowCheck,
            builder
        )
    val builderDataLength =
        fieldManagers.cellDataLengthFieldManager.readCellDataLength(this@builderStoreDataBitsNoOverflowCheck, builder)

    val updatedLength = mkSizeAddExpr(builderDataLength, mkSizeExpr(bits.sort.sizeBits.toInt()))
    val updatedLengthUpperBound =
        fieldManagers.cellDataLengthFieldManager.getUpperBound(this, builder)?.let {
            it + bits.sort.sizeBits.toInt()
        }

    val updatedData: UExpr<TvmCellDataSort> =
        if (builderDataLength is KBitVecValue) {
            val size = builderDataLength.intValue()
            val updatedData =
                if (size > 0) {
                    val oldData = mkBvExtractExpr(high = MAX_DATA_LENGTH - 1, low = MAX_DATA_LENGTH - size, builderData)
                    mkBvConcatExpr(oldData, bits)
                } else {
                    bits
                }

            val updatedDataSizeBits = updatedData.sort.sizeBits

            if (updatedDataSizeBits < CELL_DATA_BITS) {
                mkBvConcatExpr(
                    updatedData,
                    mkBv(0, CELL_DATA_BITS - updatedDataSizeBits)
                )
            } else {
                updatedData
            }.uncheckedCast()
        } else {
            updateBuilderData(builderData, bits.zeroExtendToSort(builderData.sort), updatedLength)
        }

    fieldManagers.cellDataFieldManager.writeCellData(this@builderStoreDataBitsNoOverflowCheck, builder, updatedData)
    fieldManagers.cellDataLengthFieldManager.writeCellDataLength(
        this@builderStoreDataBitsNoOverflowCheck,
        builder,
        value = updatedLength,
        upperBound = updatedLengthUpperBound
    )
}

fun TvmStepScopeManager.builderStoreDataBits(
    builder: UConcreteHeapRef,
    bits: UExpr<UBvSort>,
): Unit? =
    with(ctx) {
        val sizeBits = bits.sort.sizeBits.toInt()

        setBuilderLengthOrThrowCellOverflow(
            this@builderStoreDataBits,
            oldBuilder = builder,
            newBuilder = builder,
            writeSizeBits = mkSizeExpr(sizeBits),
            writeSizeBitsUpperBound = sizeBits,
            doNotUpdateLength = true
        ) ?: return null

        calcOnState {
            builderStoreDataBitsNoOverflowCheck(builder, bits)
        }
    }

fun <S : UBvSort> TvmStepScopeManager.builderStoreDataBits(
    oldBuilder: UHeapRef,
    builder: UConcreteHeapRef,
    bits: UExpr<S>,
    sizeBits: UExpr<TvmSizeSort>,
    quietBlock: (TvmState.() -> Unit)? = null,
): Unit? =
    with(ctx) {
        val oldBuilderDataLength =
            calcOnState {
                fieldManagers.cellDataLengthFieldManager.readCellDataLength(this, oldBuilder)
            }
        val newDataLength = mkSizeAddExpr(oldBuilderDataLength, sizeBits)
        val extendedBits = bits.zeroExtendToSort(cellDataSort)

        val writeSizeBitsUpperBound = if (sizeBits is KInterpretedValue) sizeBits.intValue() else null

        setBuilderLengthOrThrowCellOverflow(
            this@builderStoreDataBits,
            oldBuilder = oldBuilder,
            newBuilder = builder,
            writeSizeBits = sizeBits,
            writeSizeBitsUpperBound = writeSizeBitsUpperBound,
            quietBlock = quietBlock
        ) ?: return null

        val trashBits = mkSizeSubExpr(mkSizeExpr(MAX_DATA_LENGTH), sizeBits).zeroExtendToSort(cellDataSort)
        val normalizedBits = mkBvLogicalShiftRightExpr(mkBvShiftLeftExpr(extendedBits, trashBits), trashBits)

        val builderData =
            calcOnState {
                fieldManagers.cellDataFieldManager.readCellDataForBuilderOrAllocatedCell(this, builder)
            }
        val updatedData = updateBuilderData(builderData, normalizedBits, newDataLength)

        return doWithState {
            fieldManagers.cellDataFieldManager.writeCellData(this, builder, updatedData)
        }
    }

fun TvmStepScopeManager.builderStoreInt(
    oldBuilder: UHeapRef,
    builder: UConcreteHeapRef,
    value: UExpr<TvmInt257Sort>,
    sizeBits: UExpr<TvmSizeSort>,
    isSigned: Boolean,
    sizeBitsUpperBound: Int? = null,
    quietBlock: (TvmState.() -> Unit)? = null,
): Unit? =
    with(ctx) {
        val builderData =
            calcOnState {
                fieldManagers.cellDataFieldManager.readCellDataForBuilderOrAllocatedCell(this, builder)
            }

        setBuilderLengthOrThrowCellOverflow(
            scope = this@builderStoreInt,
            oldBuilder = oldBuilder,
            newBuilder = builder,
            writeSizeBits = sizeBits,
            writeSizeBitsUpperBound = sizeBitsUpperBound ?: sizeBits.intValueOrNull,
            quietBlock = quietBlock
        ) ?: return null

        val updatedLength = calcOnState { fieldManagers.cellDataLengthFieldManager.readCellDataLength(this, builder) }

        val normalizedValue =
            if (isSigned) {
                val trashBits = mkBvSubExpr(intBitsValue, sizeBits.unsignedExtendToInteger())
                mkBvLogicalShiftRightExpr(mkBvShiftLeftExpr(value, trashBits), trashBits)
            } else {
                value
            }

        val updatedData =
            updateBuilderData(builderData, normalizedValue.zeroExtendToSort(builderData.sort), updatedLength)

        return doWithState {
            fieldManagers.cellDataFieldManager.writeCellData(this, builder, updatedData)
        }
    }

/**
 *  Bitwise `and` of the lines:
 *  ```
 *   <- updBuilderLen ->
 *  |---------------bits0000000000|
 *  |builderData------------------|
 *  ```
 */
private fun TvmContext.updateBuilderData(
    builderData: UExpr<TvmCellDataSort>,
    bits: UExpr<TvmCellDataSort>,
    updatedBuilderDataLength: UExpr<TvmSizeSort>,
): UExpr<TvmCellDataSort> {
    val shiftedBits: UExpr<TvmCellDataSort> =
        mkBvShiftLeftExpr(
            bits,
            mkBvSubExpr(maxDataLengthSizeExpr, updatedBuilderDataLength).zeroExtendToSort(builderData.sort)
        )

    return mkBvOrExpr(builderData, shiftedBits)
}

private fun TvmContext.coinPrefix(value: UExpr<TvmInt257Sort>): UExpr<UBvSort> {
    val sort = mkBvSort(GRAMS_LENGTH_BITS)
    return (1..<16).fold(mkBv(0, sort) as UExpr<UBvSort>) { acc, cur ->
        val curBv = mkBv(cur, sort)
        val curBvExtended = curBv.unsignedExtendToInteger()

        // ((len - 1) * 8)
        val prevValueBits = mkBvShiftLeftExpr(mkBvSubExpr(curBvExtended, oneValue), threeValue)

        mkIte(
            mkBvSignedGreaterExpr(value, bvMaxValueUnsignedExtended(prevValueBits)),
            trueBranch = curBv,
            falseBranch = acc
        )
    }
}

/**
 * Return coin prefix (value from 0 to 15 that specifies coin length) as 4-bit bitvector.
 * */
fun TvmStepScopeManager.builderStoreGrams(
    oldBuilder: UHeapRef,
    builder: UConcreteHeapRef,
    value: UExpr<TvmInt257Sort>,
    quietBlock: (TvmState.() -> Unit)? = null,
): UExpr<KBvSort>? =
    with(ctx) {
        // var_uint$_ {n:#} len:(#< 16) value:(uint (len * 8))
        val lenSizeBits = GRAMS_LENGTH_BITS.toInt()
        val maxValue = 8 * ((1 shl lenSizeBits) - 1)

        val notOutOfRangeValue = unsignedIntegerFitsBits(value, maxValue.toUInt())
        checkOutOfRange(notOutOfRangeValue, this@builderStoreGrams)
            ?: return null

        val coinPrefix = coinPrefix(value)
        val coinPrefixExtended = coinPrefix.zeroExtendToSort(sizeSort)

        builderStoreInt(
            oldBuilder,
            builder,
            coinPrefix.unsignedExtendToInteger(),
            mkSizeExpr(lenSizeBits),
            isSigned = false,
            quietBlock = quietBlock
        ) ?: return null

        // (len * 8)
        val valueBits = mkBvShiftLeftExpr(coinPrefixExtended, threeSizeExpr)

        val sizeBitsUpperBound = valueBits.intValueOrNull ?: TvmContext.MAX_GRAMS_BITS.toInt()

        builderStoreInt(
            builder,
            builder,
            value,
            valueBits,
            isSigned = false,
            sizeBitsUpperBound = sizeBitsUpperBound,
            quietBlock
        ) ?: return null

        return coinPrefix
    }

fun TvmState.builderStoreNextRefNoOverflowCheck(
    builder: UHeapRef,
    ref: UHeapRef,
) = with(ctx) {
    val builderRefsLength = memory.readField(builder, cellRefsLengthField, sizeSort)
    writeCellRef(builder, builderRefsLength, ref)
    val updatedLength = mkSizeAddExpr(builderRefsLength, mkSizeExpr(1))
    memory.writeField(builder, cellRefsLengthField, sizeSort, updatedLength, guard = trueExpr)
}

/**
 * Return stored value
 * */
fun TvmStepScopeManager.builderStoreSlice(
    oldBuilder: UHeapRef,
    builder: UConcreteHeapRef,
    slice: UHeapRef,
    quietBlock: (TvmState.() -> Unit)? = null,
): UExpr<TvmCellDataSort>? =
    with(ctx) {
        val cell = calcOnState { memory.readField(slice, sliceCellField, addressSort) }
        val cellDataLength = calcOnState { fieldManagers.cellDataLengthFieldManager.readCellDataLength(this, cell) }

        assertDataLengthConstraintWithoutError(
            cellDataLength,
            unsatBlock = { error("Cannot ensure correctness for data length in cell $cell") }
        ) ?: return null
        val dataPosition = calcOnState { memory.readField(slice, sliceDataPosField, sizeSort) }

        // TODO: use TL-B values if possible
        val bitsToWriteLength = mkSizeSubExpr(cellDataLength, dataPosition)
        val cellData =
            slicePreloadDataBits(slice, bitsToWriteLength, quietBlock)
                ?: return null

        val cellRefsSize = calcOnState { memory.readField(cell, cellRefsLengthField, sizeSort) }
        val refsPosition = calcOnState { memory.readField(slice, sliceRefPosField, sizeSort) }
        val oldBuilderRefsSize = calcOnState { memory.readField(oldBuilder, cellRefsLengthField, sizeSort) }

        val refsToWriteSize = mkBvSubExpr(cellRefsSize, refsPosition)
        val resultingRefsSize = mkBvAddExpr(oldBuilderRefsSize, refsToWriteSize)
        val canWriteRefsConstraint = mkSizeLeExpr(resultingRefsSize, maxRefsLengthSizeExpr)

        checkCellOverflow(canWriteRefsConstraint, this@builderStoreSlice, quietBlock)
            ?: return null

        builderStoreDataBits(oldBuilder, builder, cellData, bitsToWriteLength, quietBlock)
            ?: return null

        doWithState {
            for (i in 0 until TvmContext.MAX_REFS_NUMBER) {
                val sliceRef = readCellRef(cell, mkSizeExpr(i))
                writeCellRef(builder, mkSizeAddExpr(oldBuilderRefsSize, mkSizeExpr(i)), sliceRef)
            }

            memory.writeField(builder, cellRefsLengthField, sizeSort, resultingRefsSize, guard = trueExpr)
        }

        return cellData
    }

fun TvmState.allocDataCellFromData(data: UExpr<UBvSort>): UConcreteHeapRef {
    check(data.sort.sizeBits <= CELL_DATA_BITS) { "Unexpected data: $data" }

    val cell = allocEmptyCell()

    memory.types.allocate(cell.address, TvmDataCellType)
    builderStoreDataBitsNoOverflowCheck(cell, data)

    return cell
}

fun TvmStepScopeManager.allocCellFromData(
    data: UExpr<TvmCellDataSort>,
    sizeBits: UExpr<TvmSizeSort>,
): UHeapRef =
    doWithCtx {
        val cell = calcOnState { allocEmptyCell() }
        val shiftValue =
            mkBvSubExpr(
                maxDataLengthSizeExpr.zeroExtendToSort(cellDataSort),
                sizeBits.zeroExtendToSort(cellDataSort)
            )
        val shiftedData = mkBvShiftLeftExpr(data, shiftValue)

        doWithState {
            fieldManagers.cellDataFieldManager.writeCellData(this, cell, shiftedData)
            fieldManagers.cellDataLengthFieldManager.writeCellDataLength(this, cell, sizeBits, sizeBits.intValueOrNull)
        }

        cell
    }

fun TvmState.allocSliceFromCell(data: TvmCell): UConcreteHeapRef {
    val sliceCell = allocateCell(data)

    return allocSliceFromCell(sliceCell)
}

fun TvmState.allocSliceFromData(data: UExpr<UBvSort>): UConcreteHeapRef {
    val sliceCell = allocDataCellFromData(data)

    return allocSliceFromCell(sliceCell)
}

fun TvmStepScopeManager.allocSliceFromData(
    data: UExpr<TvmCellDataSort>,
    sizeBits: UExpr<TvmSizeSort>,
): UHeapRef {
    val sliceCell = allocCellFromData(data, sizeBits)

    return calcOnStateCtx { allocSliceFromCell(sliceCell) }
}

fun TvmState.allocateCell(cellValue: TvmCell): UConcreteHeapRef =
    with(ctx) {
        val refsSizeCondition = cellValue.refs.size <= TvmContext.MAX_REFS_NUMBER
        val cellDataSizeCondition = cellValue.data.bits.length <= MAX_DATA_LENGTH
        check(refsSizeCondition && cellDataSizeCondition) { "Unexpected cellValue: $cellValue" }

        val cell = allocEmptyCell()

        if (cellValue.data.bits.isNotEmpty()) {
            val data =
                mkBv(
                    cellValue.data.bits,
                    cellValue.data.bits.length
                        .toUInt()
                )
            builderStoreDataBitsNoOverflowCheck(cell, data)
        }

        cellValue.refs.forEach { refValue ->
            val ref = allocateCell(refValue)

            builderStoreNextRefNoOverflowCheck(cell, ref)
        }

        cell
    }

fun TvmState.allocEmptyCell() =
    with(ctx) {
        memory.allocConcrete(TvmDataCellType).also { cell ->
            fieldManagers.cellDataFieldManager.writeCellData(memory, cell, mkBv(0, cellDataSort))
            fieldManagers.cellDataLengthFieldManager.writeCellDataLength(
                this@allocEmptyCell,
                cellRef = cell,
                value = zeroSizeExpr,
                upperBound = 0
            )
            memory.writeField(cell, cellRefsLengthField, sizeSort, zeroSizeExpr, trueExpr)
        }
    }

fun TvmState.allocSliceFromCell(cell: UHeapRef) =
    with(ctx) {
        memory.allocConcrete(TvmSliceType).also { slice ->
            memory.writeField(slice, sliceCellField, addressSort, cell, trueExpr)
            memory.writeField(slice, sliceDataPosField, sizeSort, zeroSizeExpr, trueExpr)
            memory.writeField(slice, sliceRefPosField, sizeSort, zeroSizeExpr, trueExpr)
        }
    }

fun TvmState.getSliceRemainingRefsCount(slice: UHeapRef): UExpr<TvmSizeSort> =
    with(ctx) {
        val cell = memory.readField(slice, sliceCellField, addressSort)
        val refsLength = memory.readField(cell, cellRefsLengthField, sizeSort)
        val refsPos = memory.readField(slice, sliceRefPosField, sizeSort)

        mkBvSubExpr(refsLength, refsPos)
    }

fun TvmState.getSliceRemainingBitsCount(slice: UHeapRef): UExpr<TvmSizeSort> =
    with(ctx) {
        val cell = memory.readField(slice, sliceCellField, addressSort)
        val dataLength =
            fieldManagers.cellDataLengthFieldManager.readCellDataLength(
                this@getSliceRemainingBitsCount,
                cell
            )
        val dataPos = memory.readField(slice, sliceDataPosField, sizeSort)

        mkBvSubExpr(dataLength, dataPos)
    }

private fun <Field, Sort : USort> UWritableMemory<*>.copyField(
    from: UHeapRef,
    to: UHeapRef,
    field: Field,
    sort: Sort,
) {
    writeField(to, field, sort, readField(from, field, sort), guard = from.ctx.trueExpr)
}

fun TvmStepScopeManager.slicesAreEqual(
    slice1: UHeapRef,
    slice2: UHeapRef,
): UBoolExpr? =
    doWithCtx {
        val dataLeft1 =
            calcOnState {
                getSliceRemainingBitsCount(slice1)
            }
        val dataLeft2 =
            calcOnState {
                getSliceRemainingBitsCount(slice2)
            }

        // TODO: optimizations here?

        val dataPosition1 =
            calcOnState {
                memory.readField(slice1, sliceDataPosField, sizeSort)
            }
        val cell1 =
            calcOnState {
                memory.readField(slice1, sliceCellField, addressSort)
            }
        val data1 =
            preloadDataBitsFromCellWithoutChecks(cell1, dataPosition1, dataLeft1)
                ?: return@doWithCtx null

        val dataPosition2 =
            calcOnState {
                memory.readField(slice2, sliceDataPosField, sizeSort)
            }
        val cell2 =
            calcOnState {
                memory.readField(slice2, sliceCellField, addressSort)
            }
        val data2 =
            preloadDataBitsFromCellWithoutChecks(cell2, dataPosition2, dataLeft2)
                ?: return@doWithCtx null

        val shift = mkBvSubExpr(mkSizeExpr(MAX_DATA_LENGTH), dataLeft1).zeroExtendToSort(cellDataSort)
        val shiftedData1 = mkBvShiftLeftExpr(data1, shift)
        val shiftedData2 = mkBvShiftLeftExpr(data2, shift)

        mkAnd(dataLeft1 eq dataLeft2, shiftedData1 eq shiftedData2)
    }

fun TvmStepScopeManager.builderToCell(builder: UConcreteHeapRef): UConcreteHeapRef =
    calcOnState {
        builderToCell(builder)
    }

fun TvmState.builderToCell(builder: UConcreteHeapRef): UConcreteHeapRef =
    memory.allocConcrete(TvmDataCellType).also {
        builderCopyFromBuilder(builder, it)
        dataCellInfoStorage.mapper.setCellInfoFromBuilder(builder, it, this)
    }

fun sliceLoadIntTlb(
    scope: TvmStepScopeManager,
    slice: UHeapRef,
    updatedSlice: UConcreteHeapRef,
    sizeBits: Int,
    isSigned: Boolean,
    action: TvmStepScopeManager.(UExpr<TvmInt257Sort>) -> Unit,
) = scope.doWithCtx {
    makeSliceTypeLoad(
        slice,
        TvmCellDataIntegerRead(mkBv(sizeBits), isSigned, Endian.BigEndian),
        updatedSlice
    ) { tlbValue ->
        val result =
            tlbValue?.expr ?: let {
                val value =
                    slicePreloadDataBits(slice, sizeBits)
                        ?: return@makeSliceTypeLoad

                if (isSigned) {
                    value.signedExtendToInteger()
                } else {
                    value.unsignedExtendToInteger()
                }
            }

        doWithState {
            sliceMoveDataPtr(updatedSlice, sizeBits)
        }

        action(result)
    }
}

fun sliceLoadAddrTlb(
    scope: TvmStepScopeManager,
    slice: UHeapRef,
    updatedSlice: UConcreteHeapRef,
    action: TvmStepScopeManager.(UHeapRef) -> Unit,
) {
    val ctx = scope.calcOnState { ctx }
    scope.makeSliceTypeLoad(slice, TvmCellDataMsgAddrRead(ctx), updatedSlice) { tlbValue ->
        calcOnStateCtx {
            val addrSlice =
                if (tlbValue != null) {
                    val addrLength = tlbValue.first
                    sliceMoveDataPtr(updatedSlice, addrLength)

                    tlbValue.second
                } else {
                    val originalCell = memory.readField(slice, sliceCellField, addressSort)
                    val dataPos = memory.readField(slice, sliceDataPosField, sizeSort)

                    checkCellDataUnderflow(
                        this@makeSliceTypeLoad,
                        originalCell,
                        minSize = mkBvAddExpr(dataPos, twoSizeExpr),
                        maxSize = null
                    ) ?: return@calcOnStateCtx

                    val tag =
                        slicePreloadDataBitsWithoutChecks(slice, sizeBits = 2)
                            ?: return@calcOnStateCtx

                    // Special case: when tag is concrete, we don't want to assert that this is StdAddress
                    // (even if our options for that are set)
                    val addrLength =
                        slicePreloadAddrLengthWithoutSetException(
                            slice,
                            mustProcessAllAddressFormats = tag is KInterpretedValue
                        ) ?: return@calcOnStateCtx
                    sliceMoveDataPtr(updatedSlice, addrLength)

                    val addrSlice =
                        calcOnState {
                            memory.allocConcrete(TvmSliceType)
                        }.also { sliceDeepCopy(slice, it) }

                    val addrRefPos = memory.readField(addrSlice, sliceRefPosField, sizeSort)
                    val addrCell =
                        memory.readField(addrSlice, sliceCellField, addressSort).let {
                            it as? UConcreteHeapRef
                                ?: error("Unexpected cell in new slice: $it")
                        }

                    // new data length to ensure that the remaining slice bits count is equal to [addrLength]
                    val addrDataLength = mkBvAddExpr(dataPos, addrLength)

                    checkCellDataUnderflow(this@makeSliceTypeLoad, originalCell, addrDataLength)
                        ?: return@calcOnStateCtx

                    fieldManagers.cellDataLengthFieldManager.writeCellDataLength(
                        this,
                        addrCell,
                        addrDataLength,
                        upperBound = addrDataLength.intValueOrNull
                    )
                    // new refs length to ensure that the remaining slice refs count is equal to 0
                    memory.writeField(addrCell, cellRefsLengthField, sizeSort, addrRefPos, guard = trueExpr)

                    calcOnState {
                        dataCellInfoStorage.mapper.addAddressSlice(addrSlice)
                    }

                    addrSlice
                }

            action(addrSlice)
        }
    }
}

fun sliceLoadRefTlb(
    scope: TvmStepScopeManager,
    slice: UHeapRef,
    updatedSlice: UConcreteHeapRef,
    action: TvmStepScopeManager.(UHeapRef) -> Unit,
) {
    scope.makeSliceRefLoad(slice, updatedSlice) {
        val ref =
            slicePreloadNextRef(slice)
                ?: return@makeSliceRefLoad

        doWithState { sliceMoveRefPtr(updatedSlice) }

        action(ref)
    }
}

fun builderStoreIntTlb(
    scope: TvmStepScopeManager,
    builder: UConcreteHeapRef,
    updatedBuilder: UConcreteHeapRef,
    value: UExpr<TvmInt257Sort>,
    sizeBits: UExpr<TvmSizeSort>,
    isSigned: Boolean = false,
    endian: Endian,
): Unit? =
    scope.doWithCtx {
        scope.doWithState {
            storeIntTlbLabelToBuilder(builder, updatedBuilder, sizeBits, value, isSigned, endian)
        }

        scope.builderStoreInt(builder, updatedBuilder, value, sizeBits, isSigned)
    }

fun builderStoreValueTlb(
    scope: TvmStepScopeManager,
    builder: UConcreteHeapRef,
    updatedBuilder: UConcreteHeapRef,
    value: UExpr<TvmCellDataSort>,
    sizeBits: UExpr<TvmSizeSort>,
): Unit? =
    scope.doWithCtx {
        scope.doWithState {
            storeCellDataTlbLabelInBuilder(builder, updatedBuilder, value, sizeBits)
        }
        scope.builderStoreDataBits(builder, updatedBuilder, value, sizeBits)
    }

fun builderStoreGramsTlb(
    scope: TvmStepScopeManager,
    builder: UConcreteHeapRef,
    updatedBuilder: UConcreteHeapRef,
    grams: UExpr<TvmInt257Sort>,
): Unit? =
    scope.doWithCtx {
        val length =
            scope.builderStoreGrams(builder, updatedBuilder, grams)
                ?: return@doWithCtx null

        scope.doWithState {
            storeCoinTlbLabelToBuilder(builder, updatedBuilder, length, grams)
        }
    }

fun builderStoreSliceTlb(
    scope: TvmStepScopeManager,
    builder: UConcreteHeapRef,
    updatedBuilder: UConcreteHeapRef,
    slice: UHeapRef,
    quietBlock: (TvmState.() -> Unit)? = null,
): Unit? =
    scope.doWithCtx {
        builderStoreSlice(builder, updatedBuilder, slice, quietBlock)
            ?: return@doWithCtx null

        storeSliceTlbLabelInBuilder(builder, updatedBuilder, slice)
    }

fun TvmState.readSliceDataPos(slice: UHeapRef) = memory.readField(slice, sliceDataPosField, ctx.sizeSort)

fun TvmState.readSliceCell(slice: UHeapRef) = memory.readField(slice, sliceCellField, ctx.addressSort)
