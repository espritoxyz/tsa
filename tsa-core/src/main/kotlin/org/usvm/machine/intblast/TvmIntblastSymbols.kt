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
import org.usvm.machine.TvmContext.Companion.tctx

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

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { lhs },
            { rhs },
        )

    override fun internHashCode(): Int = hash(lhs, rhs)

    init {
        check(lhs.sort == rhs.sort) {
            "Incompatible sorts"
        }
    }

    fun transformToBv(transformArg: (UExpr<Sort>) -> UExpr<Sort>): UExpr<Sort> {
        val x = transformArg(lhs)
        val y = transformArg(rhs)
        return transformToBv(x, y)
    }

    companion object {
        fun <Sort : UBvSort> transformToBv(
            x: UExpr<Sort>,
            y: UExpr<Sort>,
        ): UExpr<Sort> =
            with(x.ctx.tctx()) {
                val zero = zeroValue.signExtendToSort(x.sort)
                val minusOne = minusOneValue.signExtendToSort(x.sort)

                val isNegative = mkBvSignedLessExpr(x, zero) xor mkBvSignedLessExpr(y, zero)
                val computedDiv = mkBvSignedDivExpr(x, y)
                val computedMod = mkBvSignedModExpr(x, y)
                val needToCorrect = isNegative and (computedMod neq zero)

                return mkIte(
                    needToCorrect,
                    trueBranch = { mkBvAddExpr(computedDiv, minusOne) },
                    falseBranch = { computedDiv },
                )
            }
    }
}

class TvmSignedModulo<Sort : UBvSort>(
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
        printer.append("tvm_signed_mod")
        printer.append("(")
        printer.append(lhs)
        printer.append(" % ")
        printer.append(rhs)
        printer.append(")")
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { lhs },
            { rhs },
        )

    override fun internHashCode(): Int = hash(lhs, rhs)

    init {
        check(lhs.sort == rhs.sort) {
            "Incompatible sorts"
        }
    }

    fun transformToBv(transformArg: (UExpr<Sort>) -> UExpr<Sort>): UExpr<Sort> {
        val x = transformArg(lhs)
        val y = transformArg(rhs)
        return transformToBv(x, y)
    }

    companion object {
        fun <Sort : UBvSort> transformToBv(
            x: UExpr<Sort>,
            y: UExpr<Sort>,
        ): UExpr<Sort> =
            with(x.ctx.tctx()) {
                mkBvSignedModExpr(x, y)
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

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { lhs },
            { rhs },
        )

    override fun internHashCode(): Int = hash(lhs, rhs)

    init {
        check(lhs.sort == rhs.sort) {
            "Incompatible sorts"
        }
    }

    fun transformToBv(transformArg: (UExpr<Sort>) -> UExpr<Sort>): UExpr<Sort> {
        val x = transformArg(lhs)
        val y = transformArg(rhs)
        return transformToBv(x, y)
    }

    companion object {
        fun <Sort : UBvSort> transformToBv(
            x: UExpr<Sort>,
            y: UExpr<Sort>,
        ): UExpr<Sort> =
            with(x.ctx.tctx()) {
                return mkBvMulExpr(x, y)
            }
    }
}

class TvmAddition<Sort : UBvSort>(
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
        printer.append("tvm_add")
        printer.append("(")
        printer.append(lhs)
        printer.append(" + ")
        printer.append(rhs)
        printer.append(")")
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { lhs },
            { rhs },
        )

    override fun internHashCode(): Int = hash(lhs, rhs)

    init {
        check(lhs.sort == rhs.sort) {
            "Incompatible sorts"
        }
    }

    fun transformToBv(transformArg: (UExpr<Sort>) -> UExpr<Sort>): UExpr<Sort> {
        val x = transformArg(lhs)
        val y = transformArg(rhs)
        return transformToBv(x, y)
    }

    companion object {
        fun <Sort : UBvSort> transformToBv(
            x: UExpr<Sort>,
            y: UExpr<Sort>,
        ): UExpr<Sort> =
            with(x.ctx.tctx()) {
                return mkBvAddExpr(x, y)
            }
    }
}

class TvmNegation<Sort : UBvSort>(
    ctx: TvmContext,
    val arg: UExpr<Sort>,
    override val sort: Sort,
) : USymbol<Sort>(ctx) {
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is TvmTransformer) {
            "Unexpected transformer: $transformer"
        }
        return transformer.transform(this)
    }

    override fun print(printer: ExpressionPrinter) {
        printer.append("tvm_neg")
        printer.append("(")
        printer.append(arg)
        printer.append(")")
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { arg },
        )

    override fun internHashCode(): Int = hash(arg)

    fun transformToBv(transformArg: (UExpr<Sort>) -> UExpr<Sort>): UExpr<Sort> {
        val x = transformArg(arg)
        return transformToBv(x)
    }

    companion object {
        fun <Sort : UBvSort> transformToBv(x: UExpr<Sort>): UExpr<Sort> =
            with(x.ctx.tctx()) {
                return mkBvNegationExpr(x)
            }
    }
}
