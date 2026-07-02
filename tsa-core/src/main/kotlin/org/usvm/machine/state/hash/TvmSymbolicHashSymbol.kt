package org.usvm.machine.state.hash

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KBitVecValue
import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.ton.cell.Cell
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UMockSymbol
import org.usvm.USymbol
import org.usvm.machine.TvmContext
import org.usvm.machine.intblast.TvmTransformer
import org.usvm.machine.interpreter.calculateHashOfCell

sealed class TvmHashSymbol(
    ctx: TvmContext,
    val ref: UConcreteHeapRef,
) : USymbol<UBvSort>(ctx) {
    abstract val fallbackExpr: UExpr<UBvSort>
    override val sort: UBvSort
        get() = fallbackExpr.sort

    override fun print(printer: ExpressionPrinter) {
        fallbackExpr.print(printer)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
        ) { fallbackExpr }

    override fun internHashCode(): Int = hash(fallbackExpr)
}

class TvmSymbolicHashSymbol(
    ctx: TvmContext,
    ref: UConcreteHeapRef,
    fallbackMock: UMockSymbol<UBvSort>,
) : TvmHashSymbol(ctx, ref) {
    override val fallbackExpr: UMockSymbol<UBvSort> = fallbackMock

    override fun accept(transformer: KTransformerBase): KExpr<UBvSort> {
        require(transformer is TvmTransformer) {
            "Unexpected transformer: $transformer"
        }
        return transformer.transform(this)
    }
}

class TvmConstantHashSymbol(
    ctx: TvmContext,
    concreteCell: Cell,
    ref: UConcreteHeapRef,
) : TvmHashSymbol(ctx, ref) {
    override val fallbackExpr: KBitVecValue<UBvSort> =
        ctx.mkBv(calculateHashOfCell(concreteCell), ctx.mkBvSort(256u))

    override fun accept(transformer: KTransformerBase): KExpr<UBvSort> {
        require(transformer is TvmTransformer) {
            "Unexpected transformer: $transformer"
        }
        return transformer.transform(this)
    }
}
