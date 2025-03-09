package org.usvm.machine.types

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.memory.GuardedExpr
import org.usvm.memory.foldHeapRef
import org.usvm.utils.extractAddresses

class TvmDataCellLoadedTypeInfo(
    var addressToActions: PersistentMap<UConcreteHeapRef, PersistentList<Action>>
) {

    sealed interface Action {
        val guard: UBoolExpr
        val cellAddress: UConcreteHeapRef
    }

    class LoadData<ReadResult : TvmCellDataTypeReadValue>(
        override val guard: UBoolExpr,
        override val cellAddress: UConcreteHeapRef,
        val type: TvmCellDataTypeRead<ReadResult>,
        val offset: UExpr<TvmSizeSort>,
        val sliceAddress: UConcreteHeapRef,
    ) : Action

    class LoadRef(
        override val guard: UBoolExpr,
        override val cellAddress: UConcreteHeapRef,
        val refNumber: UExpr<TvmSizeSort>,
    ) : Action

    class EndOfCell(
        override val guard: UBoolExpr,
        override val cellAddress: UConcreteHeapRef,
        val offset: UExpr<TvmSizeSort>,
        val refNumber: UExpr<TvmSizeSort>,
    ) : Action

    private fun <ConcreteAction : Action> registerAction(
        cellAddress: UHeapRef,
        action: (GuardedExpr<UConcreteHeapRef>) -> ConcreteAction,
    ): List<ConcreteAction> {
        val ctx = cellAddress.ctx
        val actionList = mutableListOf<ConcreteAction>()
        val newMap = foldHeapRef(
            ref = cellAddress,
            initial = addressToActions,
            initialGuard = ctx.trueExpr,
            collapseHeapRefs = false,
            blockOnConcrete = { map, guardedExpr ->
                val ref = guardedExpr.expr
                val oldList = map.getOrDefault(ref, persistentListOf())
                val actionInstance = action(guardedExpr)
                actionList.add(actionInstance)
                val newList = oldList.add(actionInstance)
                map.put(ref, newList)
            },
            staticIsConcrete = true,
            blockOnSymbolic = { _, ref -> error("Unexpected symbolic ref ${ref.expr}") }
        )

        addressToActions = newMap

        return actionList
    }

    context(TvmContext)
    fun <ReadResult : TvmCellDataTypeReadValue> loadData(
        cellAddress: UHeapRef,
        offset: UExpr<TvmSizeSort>,
        type: TvmCellDataTypeRead<ReadResult>,
        slice: UHeapRef,
    ): List<LoadData<ReadResult>> {
        val staticSliceAddresses = extractAddresses(slice, extractAllocated = true, extractStatic = true)
        return staticSliceAddresses.fold(emptyList()) { acc, (sliceGuard, sliceRef) ->
            acc + registerAction(cellAddress) { ref ->
                LoadData(ref.guard and sliceGuard, ref.expr, type, offset, sliceRef)
            }
        }
    }

    fun loadRef(
        cellAddress: UHeapRef,
        refPos: UExpr<TvmSizeSort>,
    ): List<LoadRef> = registerAction(cellAddress) { ref ->
        LoadRef(ref.guard, ref.expr, refPos)
    }

    fun makeEndOfCell(
        cellAddress: UHeapRef,
        offset: UExpr<TvmSizeSort>,
        refNumber: UExpr<TvmSizeSort>
    ): List<EndOfCell> = registerAction(cellAddress) { ref ->
        EndOfCell(ref.guard, ref.expr, offset, refNumber)
    }

    fun clone(): TvmDataCellLoadedTypeInfo =
        TvmDataCellLoadedTypeInfo(addressToActions)

    companion object {
        fun empty() = TvmDataCellLoadedTypeInfo(persistentMapOf())
    }
}
