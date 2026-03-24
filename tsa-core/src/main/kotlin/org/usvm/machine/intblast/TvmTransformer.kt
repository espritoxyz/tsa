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
import org.usvm.machine.interpreter.makeMod
import org.usvm.machine.types.TvmType
import org.usvm.memory.UReadOnlyMemory
import org.usvm.solver.UExprTranslator

interface TvmTransformer : KTransformerBase {
    fun <Sort : KBvSort> transform(expr: TvmSignedDivision<Sort>): UExpr<Sort>
    fun <Sort : KBvSort> transform(expr: TvmMultiplication<Sort>): UExpr<Sort>
}

interface TvmBvTransformer : TvmTransformer {
    override fun <Sort : KBvSort> transform(
        expr: TvmSignedDivision<Sort>,
    ): UExpr<Sort> = with(expr.ctx.tctx()) {
        val x = apply(expr.lhs)
        val y = apply(expr.rhs)

        val xExtended = mkBvSignExtensionExpr(1, x)
        val yExtended = mkBvSignExtensionExpr(1, y)

        val resultExtended =
            mkBvSignedDivExpr(mkBvSubExpr(xExtended, makeMod(xExtended, yExtended)), yExtended)

        return resultExtended.extractToSort(expr.sort)
    }

    override fun <Sort : KBvSort> transform(expr: TvmMultiplication<Sort>): UExpr<Sort> = with(expr.ctx.tctx()) {
        mkBvMulExpr(apply(expr.lhs), apply(expr.rhs))
    }
}

class TvmBvTranslator(ctx: TvmContext) : KNonRecursiveTransformer(ctx), TvmBvTransformer {
    override fun <Sort : KBvSort> transform(expr: TvmSignedDivision<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            with(ctx.tctx()) {
                val xExtended = mkBvSignExtensionExpr(1, l)
                val yExtended = mkBvSignExtensionExpr(1, r)

                val resultExtended =
                    mkBvSignedDivExpr(mkBvSubExpr(xExtended, makeMod(xExtended, yExtended)), yExtended)

                resultExtended.extractToSort(expr.sort)
            }
        }

    override fun <Sort : KBvSort> transform(expr: TvmMultiplication<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            with(ctx.tctx()) {
                mkBvMulExpr(l, r)
            }
        }
}

class TvmComposer(
    ctx: TvmContext,
    memory: UReadOnlyMemory<TvmType>,
    ownership: MutabilityOwnership,
) : UComposer<TvmType, TvmSizeSort>(ctx, memory, ownership), TvmBvTransformer

class TvmTranslator(ctx: TvmContext) : UExprTranslator<TvmType, TvmSizeSort>(ctx), TvmTransformer {
    override fun <Sort : KBvSort> transform(expr: TvmSignedDivision<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            ctx.tctx().mkTvmSignedDiv(l, r)
        }

    override fun <Sort : KBvSort> transform(expr: TvmMultiplication<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            ctx.tctx().mkTvmMul(l, r)
        }
}
