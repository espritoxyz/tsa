package org.usvm.machine.intblast

import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.solver.wrapper.bv2int.DisjointSetUnion
import io.ksmt.solver.wrapper.bv2int.KBv2IntContext
import io.ksmt.solver.wrapper.bv2int.KBv2IntRewriter
import io.ksmt.solver.wrapper.bv2int.KBv2IntRewriter.WrapMode.NORMALIZED_SIGNED
import io.ksmt.solver.wrapper.bv2int.KBv2IntRewriterConfig
import io.ksmt.sort.KBvSort
import io.ksmt.sort.KIntSort
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.machine.state.TsaAccountId
import org.usvm.machine.state.hash.TvmConstantHashSymbol
import org.usvm.machine.state.hash.TvmSymbolicHashSymbol

class TvmBv2IntRewriter(
    ctx: KContext,
    bv2IntContext: KBv2IntContext,
    dsu: DisjointSetUnion,
    config: KBv2IntRewriterConfig,
) : KBv2IntRewriter(ctx, bv2IntContext, dsu, config),
    TvmTransformer {
    override fun <Sort : KBvSort> transform(expr: TvmSignedDivision<Sort>): UExpr<Sort> =
        transformExprAfterTransformedBv2Int(
            expr = expr,
            dependency0 = expr.lhs,
            dependency1 = expr.rhs,
            preprocessMode = NORMALIZED_SIGNED,
            postRewriteMode = NORMALIZED_SIGNED,
        ) { arg0: KExpr<KIntSort>, arg1: KExpr<KIntSort> ->
            ctx.mkArithDiv(arg0, arg1)
        }

    override fun <Sort : KBvSort> transform(expr: TvmMultiplication<Sort>): UExpr<Sort> =
        transformExprAfterTransformedBv2Int(
            expr = expr,
            dependency0 = expr.lhs,
            dependency1 = expr.rhs,
            preprocessMode = NORMALIZED_SIGNED,
            postRewriteMode = NORMALIZED_SIGNED,
        ) { arg0: KExpr<KIntSort>, arg1: KExpr<KIntSort> ->
            ctx.mkArithMul(arg0, arg1)
        }

    override fun <Sort : KBvSort> transform(expr: TvmSignedModulo<Sort>): UExpr<Sort> =
        transformExprAfterTransformedBv2Int(
            expr = expr,
            dependency0 = expr.lhs,
            dependency1 = expr.rhs,
            preprocessMode = NORMALIZED_SIGNED,
            postRewriteMode = NORMALIZED_SIGNED,
        ) { arg0: KExpr<KIntSort>, arg1: KExpr<KIntSort> ->
            ctx.mkIntMod(arg0, arg1)
        }

    override fun transform(expr: TvmSymbolicHashSymbol): UExpr<UBvSort> {
        error("Should be removed by now")
    }

    override fun transform(expr: TvmConstantHashSymbol): UExpr<UBvSort> {
        error("Should be removed by now")
    }

    override fun transform(expr: TsaAccountId): UExpr<UBvSort> {
        error("Should be removed by now")
    }
}
