package org.usvm.machine.state

import org.ton.Endian
import org.usvm.StateId
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.types.TvmSliceType
import org.usvm.machine.types.makeCellToSlice

fun builderStoreIntTransaction(
    scope: TvmStepScopeManager,
    builder: UConcreteHeapRef,
    value: UExpr<TvmInt257Sort>,
    sizeBits: UExpr<TvmSizeSort>,
    isSigned: Boolean = false,
): Unit? = builderStoreIntTlb(scope, builder, builder, value, sizeBits, isSigned, Endian.BigEndian)

fun builderStoreGramsTransaction(
    scope: TvmStepScopeManager,
    builder: UConcreteHeapRef,
    grams: UExpr<TvmInt257Sort>,
): Unit? = builderStoreGramsTlb(scope, builder, builder, grams)

fun builderStoreSliceTransaction(
    scope: TvmStepScopeManager,
    builder: UConcreteHeapRef,
    slice: UHeapRef,
): Unit? = builderStoreSliceTlb(scope, builder, builder, slice)

fun makeCellToSliceNoFork(
    scope: TvmStepScopeManager,
    cell: UHeapRef,
    slice: UConcreteHeapRef,
) {
    val originalStateId = scope.calcOnState { id }

    scope.doNotKillScopeOnDoWithConditions = true

    scope.makeCellToSlice(cell, slice) {
        validateSliceLoadState(originalStateId)
    }

    scope.doNotKillScopeOnDoWithConditions = false
}

fun sliceLoadIntTransaction(
    scope: TvmStepScopeManager,
    slice: UHeapRef,
    sizeBits: Int,
    isSigned: Boolean = false,
): Pair<UHeapRef, UExpr<TvmInt257Sort>>? {
    var result: UExpr<TvmInt257Sort>? = null
    val originalStateId = scope.calcOnState { id }
    val updatedSliceAddress = scope.calcOnState { memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) } }

    scope.doNotKillScopeOnDoWithConditions = true

    sliceLoadIntTlb(scope, slice, updatedSliceAddress, sizeBits, isSigned) { value ->
        validateSliceLoadState(originalStateId)

        result = value
    }

    scope.doNotKillScopeOnDoWithConditions = false

    return result?.let { updatedSliceAddress to it }
}

fun sliceLoadAddrTransaction(
    scope: TvmStepScopeManager,
    slice: UHeapRef,
): Pair<UHeapRef, UHeapRef>? =
    scope.doWithCtx {
        var result: UHeapRef? = null
        val originalStateId = scope.calcOnState { id }
        val updatedSlice =
            scope.calcOnState {
                memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) }
            }

        scope.doNotKillScopeOnDoWithConditions = true

        sliceLoadAddrTlb(scope, slice, updatedSlice) { value ->
            validateSliceLoadState(originalStateId)

            result = value
        }

        scope.doNotKillScopeOnDoWithConditions = false

        result?.let { updatedSlice to it }
    }

fun sliceLoadGramsTransaction(
    scope: TvmStepScopeManager,
    slice: UHeapRef,
): Pair<UHeapRef, UExpr<TvmInt257Sort>>? {
    var resGrams: UExpr<TvmInt257Sort>? = null
    val originalStateId = scope.calcOnState { id }
    val updatedSlice =
        scope.calcOnState {
            memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) }
        }

    scope.doNotKillScopeOnDoWithConditions = true

    sliceLoadGramsTlb(scope, slice, updatedSlice) { grams ->
        validateSliceLoadState(originalStateId)

        resGrams = grams
    }

    scope.doNotKillScopeOnDoWithConditions = false

    return resGrams?.let { updatedSlice to it }
}

fun sliceLoadRefTransaction(
    scope: TvmStepScopeManager,
    slice: UHeapRef,
): Pair<UHeapRef, UHeapRef>? {
    var result: UHeapRef? = null
    val originalStateId = scope.calcOnState { id }
    val updatedSlice =
        scope.calcOnState {
            memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) }
        }

    scope.doNotKillScopeOnDoWithConditions = true

    sliceLoadRefTlb(scope, slice, updatedSlice) { value ->
        validateSliceLoadState(originalStateId)

        result = value
    }

    scope.doNotKillScopeOnDoWithConditions = false

    return result?.let { updatedSlice to it }
}

private fun TvmStepScopeManager.validateSliceLoadState(originalStateId: StateId) =
    doWithState {
        require(id == originalStateId) {
            "Forks are not supported here"
        }
    }
