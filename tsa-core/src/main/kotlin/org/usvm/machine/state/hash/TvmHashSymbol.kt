package org.usvm.machine.state.hash

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UMockSymbol
import org.usvm.USymbol
import org.usvm.machine.TvmContext
import org.usvm.machine.intblast.TvmTransformer

/**
 * @param mightBeEqualToConstant might be false if the assumed ref is symbolic, and thus it is impossible to
 * solve the constraints of form `hash(ref) == const`.
 * We want to avoid these equations, because the presense of hashes in the path constraints forces us to fixate them,
 * which makes the solving process to become nondeterministic, as the fixation of values depends on the actual model
 * (returned from the solver) for the current path constraints.
 */
class TvmHashSymbol(
    ctx: TvmContext,
    val ref: UConcreteHeapRef,
    val fallbackMock: UMockSymbol<UBvSort>,
    val mightBeEqualToConstant: Boolean = true,
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
