package org.usvm.machine.types

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.isFalse
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.state.TvmState
import org.usvm.memory.GuardedExpr
import org.usvm.utils.extractAddressesSpecialized

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
        actionList: MutableList<ConcreteAction>,
        action: (GuardedExpr<UConcreteHeapRef>) -> ConcreteAction?,
    ) = with(cellAddress.ctx as TvmContext) {
        val cellLeaves = extractAddressesSpecialized(cellAddress, extractAllocated = true, extractStatic = true)

        val newMap = cellLeaves.fold(addressToActions) { map, (guard, ref) ->
            val actionInstance = action(GuardedExpr(ref, guard))
            if (actionInstance != null) {
                actionList.add(actionInstance)
                val oldList = map.getOrDefault(ref, persistentListOf())
                val newList = oldList.add(actionInstance)
                map.put(ref, newList)
            } else {
                map
            }
        }

        addressToActions = newMap
    }

    fun <ReadResult : TvmCellDataTypeReadValue> loadData(
        state: TvmState,
        offset: UExpr<TvmSizeSort>,
        type: TvmCellDataTypeRead<ReadResult>,
        slice: UHeapRef,
    ): List<LoadData<ReadResult>> = with(state.ctx) {
        val staticSliceAddresses = extractAddressesSpecialized(slice, extractAllocated = true, extractStatic = true)

        val result = mutableListOf<LoadData<ReadResult>>()
        staticSliceAddresses.forEach { (sliceGuard, sliceRef) ->
            val cellAddress = state.memory.readField(sliceRef, TvmContext.sliceCellField, addressSort)
            registerAction(cellAddress, result) { ref ->
                val guard = (ref.guard and sliceGuard)
                if (guard.isFalse) {
                    null
                } else {
                    LoadData(guard, ref.expr, type, offset, sliceRef)
                }
            }
        }

        return result
    }

    fun loadRef(
        cellAddress: UHeapRef,
        refPos: UExpr<TvmSizeSort>,
    ): List<LoadRef> {
        val result = mutableListOf<LoadRef>()
        registerAction(cellAddress, result) { ref ->
            LoadRef(ref.guard, ref.expr, refPos)
        }
        return result
    }

    fun makeEndOfCell(
        cellAddress: UHeapRef,
        offset: UExpr<TvmSizeSort>,
        refNumber: UExpr<TvmSizeSort>
    ): List<EndOfCell> {
        val result = mutableListOf<EndOfCell>()
        registerAction(cellAddress, result) { ref ->
            EndOfCell(ref.guard, ref.expr, offset, refNumber)
        }
        return result
    }

    fun clone(): TvmDataCellLoadedTypeInfo =
        TvmDataCellLoadedTypeInfo(addressToActions)

    companion object {
        fun empty() = TvmDataCellLoadedTypeInfo(persistentMapOf())
    }
}
