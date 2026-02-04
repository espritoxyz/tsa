package org.usvm.machine.state

import org.usvm.StateId
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.types.CellRef
import org.usvm.machine.types.TvmSliceType
import org.usvm.machine.types.asCellRef
import org.usvm.machine.types.makeCellToSlice

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

fun sliceLoadIntTlbNoFork(
    scope: TvmStepScopeManager,
    slice: UHeapRef,
    sizeBits: Int,
    isSigned: Boolean = false,
    quietBlock: (TvmState.() -> Unit)? = null,
): Pair<UHeapRef, UExpr<TvmInt257Sort>>? {
    var result: UExpr<TvmInt257Sort>? = null
    val originalStateId = scope.calcOnState { id }
    val updatedSliceAddress = scope.calcOnState { memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) } }

    scope.doNotKillScopeOnDoWithConditions = true

    sliceLoadIntTlb(scope, slice, updatedSliceAddress, sizeBits, isSigned, quietBlock = quietBlock) { value ->
        validateSliceLoadState(originalStateId)

        result = value
    }

    scope.doNotKillScopeOnDoWithConditions = false

    return result?.let { updatedSliceAddress to it }
}

fun sliceLoadAddrTlbNoFork(
    scope: TvmStepScopeManager,
    slice: UHeapRef,
    quietBlock: (TvmState.() -> Unit)? = null,
): Pair<UHeapRef, UHeapRef>? =
    scope.doWithCtx {
        var result: UHeapRef? = null
        val originalStateId = scope.calcOnState { id }
        val updatedSlice =
            scope.calcOnState {
                memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) }
            }

        scope.doNotKillScopeOnDoWithConditions = true

        sliceLoadAddrTlb(scope, slice, updatedSlice, quietBlock = quietBlock) { value ->
            validateSliceLoadState(originalStateId)

            result = value
        }

        scope.doNotKillScopeOnDoWithConditions = false

        result?.let { updatedSlice to it }
    }

fun sliceLoadGramsTlbNoFork(
    scope: TvmStepScopeManager,
    slice: UHeapRef,
    quietBlock: (TvmState.() -> Unit)?,
): Pair<UHeapRef, UExpr<TvmInt257Sort>>? {
    var resGrams: UExpr<TvmInt257Sort>? = null
    val originalStateId = scope.calcOnState { id }
    val updatedSlice =
        scope.calcOnState {
            memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) }
        }

    scope.doNotKillScopeOnDoWithConditions = true

    sliceLoadGramsTlb(scope, slice, updatedSlice, quietBlock = quietBlock) { grams ->
        validateSliceLoadState(originalStateId)

        resGrams = grams
    }

    scope.doNotKillScopeOnDoWithConditions = false

    return resGrams?.let { updatedSlice to it }
}

fun sliceLoadRefTransaction(
    scope: TvmStepScopeManager,
    slice: UHeapRef,
    quietBlock: (TvmState.() -> Unit)? = null,
): Pair<UHeapRef, CellRef>? {
    var result: UHeapRef? = null
    val originalStateId = scope.calcOnState { id }
    val updatedSlice =
        scope.calcOnState {
            memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) }
        }
    scope.doNotKillScopeOnDoWithConditions = true
    sliceLoadRefTlb(scope, slice, updatedSlice, quietBlock = quietBlock) { value ->
        validateSliceLoadState(originalStateId)

        result = value
    }
    scope.doNotKillScopeOnDoWithConditions = false
    return result?.let { updatedSlice to it.asCellRef() }
}

private fun TvmStepScopeManager.validateSliceLoadState(originalStateId: StateId) =
    doWithState {
        require(id == originalStateId) {
            "Forks are not supported here"
        }
    }
