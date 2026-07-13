package org.usvm.machine.state.hash

import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.intblast.TvmBvTransformer
import org.usvm.machine.state.TsaAccountIdSymbol
import org.usvm.machine.types.TvmType
import org.usvm.solver.UExprTranslator

open class HashCollector(
    ctx: TvmContext,
) : UExprTranslator<TvmType, TvmSizeSort>(ctx),
    TvmBvTransformer {
    val collectedHashes = hashSetOf<TvmSymbolicHashSymbol>()

    override fun transform(expr: TvmSymbolicHashSymbol): UExpr<UBvSort> {
        collectedHashes.add(expr)
        return super<TvmBvTransformer>.transform(expr)
    }

    override fun transform(expr: TsaAccountIdSymbol): UExpr<UBvSort> = expr
}
