package org.usvm.machine.fields

import org.ton.bytecode.TvmField
import org.ton.bytecode.TvmFieldImpl
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.machine.TvmContext
import org.usvm.machine.state.TvmState
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmType
import org.usvm.memory.UWritableMemory

class TvmCellExoticFieldManager(
    private val ctx: TvmContext,
) {
    fun clone(): TvmCellExoticFieldManager =
        TvmCellExoticFieldManager(
            ctx,
        )

    fun writeCellData(
        state: TvmState,
        cellRef: UHeapRef,
        value: UBoolExpr,
    ) = writeCellData(state.memory, cellRef, value)

    fun writeCellData(
        memory: UWritableMemory<TvmType>,
        cellRef: UHeapRef,
        value: UBoolExpr,
    ) = with(ctx) {
        memory.writeField(cellRef, isExoticField, boolSort, value, guard = trueExpr)
    }

    fun readCellData(
        state: TvmState,
        cellRef: UConcreteHeapRef,
    ): UBoolExpr =
        with(ctx) {
            state.memory.readField(cellRef, isExoticField, boolSort)
        }

    companion object {
        private val isExoticField: TvmField = TvmFieldImpl(TvmCellType, "isExotic")
    }
}
