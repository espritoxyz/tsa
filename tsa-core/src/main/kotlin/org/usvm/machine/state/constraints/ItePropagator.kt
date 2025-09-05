package org.usvm.machine.state.constraints

import io.ksmt.KContext
import io.ksmt.expr.KBvAddExpr
import io.ksmt.expr.KExpr
import io.ksmt.expr.KIteExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KNonRecursiveTransformer
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBvSort
import io.ksmt.sort.KSort

class ItePropagator(context: KContext) : KNonRecursiveTransformer(context) {

    override fun <T : KBvSort> transform(expr: KBvAddExpr<T>): KExpr<T> =
        transformExprAfterTransformedIte(expr, expr.arg0, expr.arg1, ctx::mkBvAddExpr)

    private inline fun <T : KSort, A0 : KSort, A1 : KSort> transformExprAfterTransformedIte(
        expr: KExpr<T>,
        dependency0: KExpr<A0>,
        dependency1: KExpr<A1>,
        constructor: (KExpr<A0>, KExpr<A1>) -> KExpr<T>
    ): KExpr<T> = with(ctx) {
        transformExprAfterTransformed(expr, dependency0, dependency1) { transformedDependency0, transformedDependency1 ->
            when {
                transformedDependency0 is KIteExpr -> {
                    mkIte(
                        transformedDependency0.condition,
                        constructor(transformedDependency0.trueBranch, transformedDependency1),
                        constructor(transformedDependency0.falseBranch, transformedDependency1)
                    )
                }
                transformedDependency1 is KIteExpr -> {
                    mkIte(
                        transformedDependency1.condition,
                        constructor(transformedDependency0, transformedDependency1.trueBranch),
                        constructor(transformedDependency0, transformedDependency1.falseBranch)
                    )
                }
                else -> constructor(transformedDependency0, transformedDependency1)
            }


            TODO()
        }
    }

    private class ComplexIte<T : KSort>(
        ctx: KContext,
        val conditions: List<KExpr<KBoolSort>>,
        val branches: List<KExpr<T>>,
    ) : KExpr<T>(ctx) {
        override val sort: T
            get() = branches.first().sort

        override fun accept(transformer: KTransformerBase): KExpr<T> {
            TODO("Not yet implemented")
        }

        override fun internEquals(other: Any): Boolean {
            TODO("Not yet implemented")
        }

        override fun internHashCode(): Int {
            TODO("Not yet implemented")
        }

        override fun print(printer: ExpressionPrinter) {
            TODO("Not yet implemented")
        }
    }
}