package org.usvm.machine.intblast

import io.ksmt.expr.KBvAndExpr
import io.ksmt.expr.KBvOrExpr
import io.ksmt.expr.transformer.KNonRecursiveTransformer
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KBvSort
import io.ksmt.utils.uncheckedCast
import org.usvm.UBvSort
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.state.TsaAccountIdSymbol
import org.usvm.machine.state.hash.TvmConstantHashSymbol
import org.usvm.machine.state.hash.TvmSymbolicHashSymbol
import org.usvm.machine.types.TvmType
import org.usvm.memory.UReadOnlyMemory
import org.usvm.solver.UExprTranslator
import org.usvm.utils.groupIntoParts

interface TvmTransformer : KTransformerBase {
    fun <Sort : KBvSort> transform(expr: TvmSignedDivision<Sort>): UExpr<Sort>

    fun <Sort : KBvSort> transform(expr: TvmMultiplication<Sort>): UExpr<Sort>

    fun <Sort : KBvSort> transform(expr: TvmSignedModulo<Sort>): UExpr<Sort>

    fun transform(expr: TvmSymbolicHashSymbol): UExpr<UBvSort>

    fun transform(expr: TvmConstantHashSymbol): UExpr<UBvSort>

    fun transform(expr: TsaAccountIdSymbol): UExpr<UBvSort>
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

    override fun transform(expr: TvmSymbolicHashSymbol): UExpr<UBvSort> = apply(expr.fallbackExpr)

    override fun transform(expr: TvmConstantHashSymbol): UExpr<UBvSort> = apply(expr.fallbackExpr)

    override fun transform(expr: TsaAccountIdSymbol): UExpr<UBvSort> =
        expr.ctx.tctx().mkTsaAccountIdSymbol(
            expr.code,
            expr.data,
            apply(expr.isStateInit),
            apply(expr.boundStateInitHash),
            apply(expr.symbolicAccountId),
        )
}

class TvmBvNonRecursiveTransformer(
    ctx: TvmContext,
) : KNonRecursiveTransformer(ctx),
    TvmBvTransformer {
    var visitedHardExpression = false

    override fun <Sort : KBvSort> transform(expr: TvmSignedDivision<Sort>): UExpr<Sort> {
        visitedHardExpression = true
        return transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            TvmSignedDivision.transformToBv(l, r)
        }
    }

    override fun <Sort : KBvSort> transform(expr: TvmMultiplication<Sort>): UExpr<Sort> {
        visitedHardExpression = true
        return transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            TvmMultiplication.transformToBv(l, r)
        }
    }

    override fun <Sort : KBvSort> transform(expr: TvmSignedModulo<Sort>): UExpr<Sort> {
        visitedHardExpression = true
        return transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            TvmSignedModulo.transformToBv(l, r)
        }
    }
}

class TvmComposer(
    ctx: TvmContext,
    memory: UReadOnlyMemory<TvmType>,
    ownership: MutabilityOwnership,
) : UComposer<TvmType, TvmSizeSort>(ctx, memory, ownership),
    TvmBvTransformer {
    override fun transform(expr: TsaAccountIdSymbol): UExpr<UBvSort> =
        transformExprAfterTransformed(
            expr,
            expr.symbolicAccountId,
            expr.isStateInit,
            expr.code,
            expr.data,
            expr.boundStateInitHash,
        ) { symbAccId, newIsStateinit, newCode, newData, newBoundStateInitHash ->
            ctx.mkIte(newIsStateinit, newBoundStateInitHash, symbAccId)
        }
}

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
            ctx.tctx().mkTvmMulNoSimplify(l, r)
        }

    override fun <Sort : KBvSort> transform(expr: TvmSignedModulo<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            ctx.tctx().mkTvmSignedMod(l, r)
        }

    override fun transform(expr: TvmSymbolicHashSymbol): UExpr<UBvSort> =
        transformExprAfterTransformed(expr, expr.fallbackExpr) { it }

    override fun transform(expr: TvmConstantHashSymbol): UExpr<UBvSort> =
        transformExprAfterTransformed(expr, expr.fallbackExpr) { it }

    override fun <Sort : KBvSort> transform(expr: KBvOrExpr<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.arg0, expr.arg1) { l, r ->
            with(ctx.tctx()) {
                val groups = groupIntoParts(l.uncheckedCast(), r.uncheckedCast())
                if (groups != null && groups.size > 1) {
                    val propagated = groups.map { mkBvOrExpr(it.first, it.second) }
                    return propagated.reduce { acc, expr -> mkBvConcatExpr(acc, expr) }.uncheckedCast()
                }
                mkBvOrExpr(l, r)
            }
        }

    override fun <Sort : KBvSort> transform(expr: KBvAndExpr<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.arg0, expr.arg1) { l, r ->
            with(ctx.tctx()) {
                val groups = groupIntoParts(l.uncheckedCast(), r.uncheckedCast())
                if (groups != null && groups.size > 1) {
                    val propagated = groups.map { mkBvAndExpr(it.first, it.second) }
                    return propagated.reduce { acc, expr -> mkBvConcatExpr(acc, expr) }.uncheckedCast()
                }
                mkBvAndExpr(l, r)
            }
        }

    override fun transform(expr: TsaAccountIdSymbol): UExpr<UBvSort> =
        transformExprAfterTransformed(
            expr,
            expr.symbolicAccountId,
            expr.isStateInit,
            expr.code,
            expr.data,
            expr.boundStateInitHash,
        ) { symbAccId, newIsStateinit, newCode, newData, newBoundStateInitHash ->
            ctx.mkIte(newIsStateinit, newBoundStateInitHash, symbAccId)
        }
}
