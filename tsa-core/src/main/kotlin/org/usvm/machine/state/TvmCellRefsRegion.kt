package org.usvm.machine.state

import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TvmSizeSort
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemory

object TvmCellRefsRegionLValue : ULValue<TvmCellRefsRegionLValue, Nothing> {
    override val key: TvmCellRefsRegionLValue
        get() = this

    override val memoryRegionId: UMemoryRegionId<TvmCellRefsRegionLValue, Nothing>
        get() = TvmCellRefsRegionId

    override val sort: Nothing
        get() = error("TvmCellRefsRegion sort should not be used")
}

object TvmCellRefsRegionId : UMemoryRegionId<TvmCellRefsRegionLValue, Nothing> {
    override fun emptyRegion(): UMemoryRegion<TvmCellRefsRegionLValue, Nothing> =
        TvmRefsMemoryRegion<TvmCellRefsRegionLValue, TvmSizeSort, UAddressSort>()

    override val sort: Nothing
        get() = error("TvmCellRefsRegion sort should not be used")
}

@Suppress("UNCHECKED_CAST")
fun UReadOnlyMemory<*>.tvmCellRefsRegion(): TvmRefsMemoryRegion<TvmCellRefsRegionLValue, TvmSizeSort, UAddressSort> =
    getRegion(TvmCellRefsRegionId) as TvmRefsMemoryRegion<TvmCellRefsRegionLValue, TvmSizeSort, UAddressSort>

class TvmCellRefsRegionValueInfo(
    private val state: TvmState,
) : TvmRefsMemoryRegion.TvmRefsRegionValueInfo<UAddressSort> {
    override fun mkDefaultValue(): UHeapRef = state.emptyRefValue.emptyCell

    override fun mkSymbolicValue(): UHeapRef = state.generateSymbolicCell()

    override fun actualizeSymbolicValue(value: UHeapRef): UExpr<UAddressSort> =
        value.also { state.ensureSymbolicCellInitialized(value) }
}

fun TvmState.readCellRef(
    cell: UHeapRef,
    refIdx: UExpr<TvmSizeSort>,
    initConstraintsForChildren: Boolean = true,
): UHeapRef {
    if (initConstraintsForChildren && stateInitialized) {
        dataCellInfoStorage.notifyAboutChildRequest(this, cell)
    }
    val region = memory.tvmCellRefsRegion()
    return region.readRefValue(cell, refIdx, TvmCellRefsRegionValueInfo(this))
}

fun TvmState.writeCellRef(
    cell: UHeapRef,
    refIdx: UExpr<TvmSizeSort>,
    ref: UHeapRef,
    guard: UBoolExpr = ref.ctx.trueExpr,
) {
    val region = memory.tvmCellRefsRegion()
    val updatedRegion = region.writeRefValue(cell, refIdx, ref, guard)
    memory.setRegion(TvmCellRefsRegionId, updatedRegion)
}

fun TvmState.copyCellRefs(
    srcCell: UHeapRef,
    dstCell: UConcreteHeapRef,
) {
    val region = memory.tvmCellRefsRegion()
    val updatedRegion = region.copyRefValues(srcCell, dstCell)
    memory.setRegion(TvmCellRefsRegionId, updatedRegion)
}
