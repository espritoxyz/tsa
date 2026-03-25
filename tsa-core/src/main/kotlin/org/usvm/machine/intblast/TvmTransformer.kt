package org.usvm.machine.intblast

import io.ksmt.expr.transformer.KNonRecursiveTransformer
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KBvSort
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.types.TvmType
import org.usvm.memory.UReadOnlyMemory
import org.usvm.solver.UExprTranslator

interface TvmTransformer : KTransformerBase {
    fun <Sort : KBvSort> transform(expr: TvmSignedDivision<Sort>): UExpr<Sort>
    fun <Sort : KBvSort> transform(expr: TvmMultiplication<Sort>): UExpr<Sort>
    fun <Sort : KBvSort> transform(expr: TvmSignedModulo<Sort>): UExpr<Sort>
}

interface TvmBvTransformer : TvmTransformer {
    override fun <Sort : KBvSort> transform(expr: TvmSignedDivision<Sort>): UExpr<Sort> =
        expr.transformToBv {
            apply(it)
        }

    override fun <Sort : KBvSort> transform(expr: TvmMultiplication<Sort>): UExpr<Sort> =
        expr.transformToBv {
            apply(it)
        }

    override fun <Sort : KBvSort> transform(expr: TvmSignedModulo<Sort>): UExpr<Sort> =
        expr.transformToBv {
            apply(it)
        }
}

class TvmBvNonRecursiveTransformer(
    ctx: TvmContext,
) : KNonRecursiveTransformer(ctx),
    TvmBvTransformer {
    override fun <Sort : KBvSort> transform(expr: TvmSignedDivision<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            TvmSignedDivision.transformToBv(l, r)
        }

    override fun <Sort : KBvSort> transform(expr: TvmMultiplication<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            TvmMultiplication.transformToBv(l, r)
        }

    override fun <Sort : KBvSort> transform(expr: TvmSignedModulo<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            TvmSignedModulo.transformToBv(l, r)
        }
}

class TvmComposer(
    ctx: TvmContext,
    memory: UReadOnlyMemory<TvmType>,
    ownership: MutabilityOwnership,
) : UComposer<TvmType, TvmSizeSort>(ctx, memory, ownership),
    TvmBvTransformer

class TvmTranslator(
    ctx: TvmContext,
) : UExprTranslator<TvmType, TvmSizeSort>(ctx),
    TvmTransformer {
    override fun <Sort : KBvSort> transform(expr: TvmSignedDivision<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            ctx.tctx().mkTvmSignedDiv(l, r)
        }

    override fun <Sort : KBvSort> transform(expr: TvmMultiplication<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            ctx.tctx().mkTvmMul(l, r)
        }

    override fun <Sort : KBvSort> transform(expr: TvmSignedModulo<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            ctx.tctx().mkTvmSignedMod(l, r)
        }
}
