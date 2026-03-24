package org.usvm.machine.intblast

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.USymbol
import org.usvm.machine.TvmContext

class TvmSignedDivision<Sort : UBvSort>(
    ctx: TvmContext,
    val lhs: UExpr<Sort>,
    val rhs: UExpr<Sort>,
    override val sort: Sort,
) : USymbol<Sort>(ctx) {
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is TvmTransformer) {
            "Unexpected transformer: $transformer"
        }
        return transformer.transform(this)
    }

    override fun print(printer: ExpressionPrinter) {
        printer.append("tvm_signed_div")
        printer.append("(")
        printer.append(lhs)
        printer.append(" / ")
        printer.append(rhs)
        printer.append(")")
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(
        other,
        { lhs },
        { rhs }
    )

    override fun internHashCode(): Int = hash(lhs, rhs)

    init {
        check(lhs.sort == rhs.sort) {
            "Incompatible sorts"
        }
    }
}

class TvmMultiplication<Sort : UBvSort>(
    ctx: TvmContext,
    val lhs: UExpr<Sort>,
    val rhs: UExpr<Sort>,
    override val sort: Sort,
) : USymbol<Sort>(ctx) {
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is TvmTransformer) {
            "Unexpected transformer: $transformer"
        }
        return transformer.transform(this)
    }

    override fun print(printer: ExpressionPrinter) {
        printer.append("tvm_mul")
        printer.append("(")
        printer.append(lhs)
        printer.append(" * ")
        printer.append(rhs)
        printer.append(")")
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(
        other,
        { lhs },
        { rhs }
    )

    override fun internHashCode(): Int = hash(lhs, rhs)

    init {
        check(lhs.sort == rhs.sort) {
            "Incompatible sorts"
        }
    }
}
