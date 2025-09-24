package org.usvm.machine.fields

import io.ksmt.expr.KBvExtractExpr
import org.ton.bytecode.TvmField
import org.ton.bytecode.TvmFieldImpl
import org.usvm.UConcreteHeapRef
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
import org.usvm.utils.splitAndRead

class TvmCellRefsLengthFieldManager(
    private val ctx: TvmContext,
) {
    private val cellRefsSort = ctx.mkBvSort(BITS_FOR_FIELD)
    private val cellRefsLengthField: TvmField = TvmFieldImpl(TvmCellType, "refsLength")

    fun readCellRefLength(
        state: TvmState,
        cellRef: UHeapRef,
    ): UExpr<TvmSizeSort> =
        with(ctx) {
            val value = state.memory.readField(cellRef, cellRefsLengthField, cellRefsSort)
            value
                .let {
                    mkIte(
                        mkBvUnsignedGreaterOrEqualExpr(it, mkBv(TvmContext.MAX_REFS_NUMBER, cellRefsSort)),
                        mkBv(TvmContext.MAX_REFS_NUMBER, cellRefsSort),
                        it,
                    )
                }.zeroExtendToSort(sizeSort)
        }

    fun writeCellRefsLength(
        state: TvmState,
        cellRef: UConcreteHeapRef,
        value: UExpr<TvmSizeSort>,
    ) {
        writeCellRefsLength(state.memory, cellRef, value)
    }

    fun writeCellRefsLength(
        memory: UWritableMemory<TvmType>,
        cellRef: UConcreteHeapRef,
        value: UExpr<TvmSizeSort>,
    ) = with(ctx) {
        val extractedValue = mkBvExtractExprNoSimplify(high = BITS_FOR_FIELD.toInt() - 1, low = 0, value)
        memory.writeField(cellRef, cellRefsLengthField, cellRefsSort, extractedValue, guard = trueExpr)
    }

    companion object {
        private const val BITS_FOR_FIELD = 3u
    }
}
