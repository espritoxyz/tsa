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

fun sliceLoadIntTransaction(
    scope: TvmStepScopeManager,
    slice: UHeapRef,
    sizeBits: Int,
    isSigned: Boolean = false,
): Pair<UHeapRef, UExpr<TvmInt257Sort>>? = scope.doWithCtx {
    var result: UExpr<TvmInt257Sort>? = null
    val originalStateId = scope.calcOnState { id }
    val updatedSliceAddress = scope.calcOnState { memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) } }

    sliceLoadIntTlb(scope, slice, updatedSliceAddress, sizeBits, isSigned) { value ->
        // hide the original [scope] from this closure
        @Suppress("NAME_SHADOWING", "UNUSED_VARIABLE")
        val scope = Unit
        validateSliceLoadState(originalStateId)

        result = value
    }

    result?.let { updatedSliceAddress to it }
}

fun sliceLoadAddrTransaction(
    scope: TvmStepScopeManager,
    slice: UHeapRef,
): Pair<UHeapRef, UHeapRef>? = scope.doWithCtx {
    var result: UHeapRef? = null
    val originalStateId = scope.calcOnState { id }
    val updatedSlice = scope.calcOnState {
        memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) }
    }

    sliceLoadAddrTlb(scope, slice, updatedSlice) { value ->
        // hide the original [scope] from this closure
        @Suppress("NAME_SHADOWING", "UNUSED_VARIABLE")
        val scope = Unit
        validateSliceLoadState(originalStateId)

        result = value
    }

    result?.let { updatedSlice to it }
}

fun sliceLoadGramsTransaction(
    scope: TvmStepScopeManager,
    slice: UHeapRef,
): Pair<UHeapRef, UExpr<TvmInt257Sort>>? {
    var resGrams: UExpr<TvmInt257Sort>? = null
    val originalStateId = scope.calcOnState { id }
    val updatedSlice = scope.calcOnState {
        memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) }
    }

    sliceLoadGramsTlb(scope, slice, updatedSlice) { grams ->
        validateSliceLoadState(originalStateId)

        resGrams = grams
    }

    return resGrams?.let { updatedSlice to it }
}

fun sliceLoadRefTransaction(
    scope: TvmStepScopeManager,
    slice: UHeapRef
): Pair<UHeapRef, UHeapRef>? {
    var result: UHeapRef? = null
    val originalStateId = scope.calcOnState { id }
    val updatedSlice = scope.calcOnState {
        memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) }
    }

    sliceLoadRefTlb(scope, slice, updatedSlice) { value ->
        // hide the original [scope] from this closure
        @Suppress("NAME_SHADOWING", "UNUSED_VARIABLE")
        val scope = Unit
        validateSliceLoadState(originalStateId)

        result = value
    }

    return result?.let { updatedSlice to it }
}

private fun TvmStepScopeManager.validateSliceLoadState(originalStateId: StateId) = doWithState {
    require(id == originalStateId) {
        "Forks are not supported here"
    }
}
