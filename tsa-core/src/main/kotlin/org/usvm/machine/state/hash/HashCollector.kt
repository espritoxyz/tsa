package org.usvm.machine.state.hash

import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.intblast.TvmBvTransformer
import org.usvm.machine.types.TvmType
import org.usvm.solver.UExprTranslator

open class HashCollector(
    ctx: TvmContext,
) : UExprTranslator<TvmType, TvmSizeSort>(ctx),
    TvmBvTransformer {
    val collectedHashes = hashSetOf<TvmHashSymbol>()

    override fun transform(expr: TvmHashSymbol): UExpr<UBvSort> {
        collectedHashes.add(expr)
        return super<TvmBvTransformer>.transform(expr)
    }
}
