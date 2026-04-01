package org.usvm.machine.state.hash

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USymbol
import org.usvm.machine.TvmContext
import org.usvm.machine.intblast.TvmTransformer

class TvmHashSymbol(
    ctx: TvmContext,
    val ref: UConcreteHeapRef,
    val fallbackMock: UExpr<UBvSort>,
) : USymbol<UBvSort>(ctx) {
    override val sort: UBvSort
        get() = fallbackMock.sort

    override fun accept(transformer: KTransformerBase): KExpr<UBvSort> {
        require(transformer is TvmTransformer) {
            "Unexpected transformer: $transformer"
        }
        return transformer.transform(this)
    }

    override fun print(printer: ExpressionPrinter) {
        fallbackMock.print(printer)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
        ) { fallbackMock }

    override fun internHashCode(): Int = hash(fallbackMock)
}
