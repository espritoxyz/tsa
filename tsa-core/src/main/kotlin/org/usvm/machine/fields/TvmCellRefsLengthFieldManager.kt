package org.usvm.machine.fields

import io.ksmt.expr.KBvExtractExpr
import org.ton.bytecode.TvmField
import org.ton.bytecode.TvmFieldImpl
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.state.TvmState
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmType
import org.usvm.memory.UWritableMemory
import org.usvm.sizeSort

class TvmCellRefsLengthFieldManager(private val ctx: TvmContext) {
    private val cellRefsSort = ctx.mkBvSort(BITS_FOR_FIELD)
    private val cellRefsLengthMinusOneField: TvmField = TvmFieldImpl(TvmCellType, "refsLengthMinusOne")
    private val noChildrenField: TvmField = TvmFieldImpl(TvmCellType, "noChildren")

    private fun refsFromField(state: TvmState, cellRef: UHeapRef): UExpr<TvmSizeSort> = with(ctx) {
        val value = state.memory.readField(cellRef, cellRefsLengthMinusOneField, cellRefsSort)
        // hack: value in this field is either input symbol or
        // some value that was written with [writeCellRefsLength].
        // If this is the last case, we can get rid of the [extract] that was added in [writeCellRefsLength].
        if (value is KBvExtractExpr && value.value.sort == sizeSort) {
            @Suppress("unchecked_cast")
            mkBvAddExpr(value.value as UExpr<TvmSizeSort>, oneSizeExpr)
        } else {
            mkBvAddExpr(value.zeroExtendToSort(sizeSort), oneSizeExpr)
        }
    }

    fun readCellRefLength(state: TvmState, cellRef: UHeapRef): UExpr<TvmSizeSort> =
        with(state.ctx) {
            val noChildren = state.memory.readField(cellRef, noChildrenField, boolSort)
            mkIte(
                noChildren,
                trueBranch = {
                    zeroSizeExpr
                },
                falseBranch = {
                    refsFromField(state, cellRef)
                }
            )
        }

    fun writeCellRefsLength(state: TvmState, cellRef: UHeapRef, value: UExpr<TvmSizeSort>){
        writeCellRefsLength(state.memory, cellRef, value)
    }

    fun writeCellRefsLength(memory: UWritableMemory<TvmType>, cellRef: UHeapRef, value: UExpr<TvmSizeSort>) = with(ctx) {
        memory.writeField(cellRef, noChildrenField, boolSort, value eq zeroSizeExpr, guard = trueExpr)

        val refsMinusOne = mkBvSubExpr(value, oneSizeExpr)
        val extractedValue = mkBvExtractExprNoSimplify(high = BITS_FOR_FIELD.toInt() - 1, low = 0, refsMinusOne)
        memory.writeField(cellRef, cellRefsLengthMinusOneField, cellRefsSort, extractedValue, guard = trueExpr)
    }

    companion object {
        private const val BITS_FOR_FIELD = 2u
    }
}
