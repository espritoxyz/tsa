package org.usvm.machine.interpreter.inputdict

import kotlinx.collections.immutable.PersistentList
import org.usvm.UBoolExpr
import org.usvm.machine.TvmContext

sealed interface LazyUniversalQuantifierConstraint {
    /**
     * Used to preserve the invariants of the input dicts.
     * In short - it is used to construct the conditions a key in the root input dictionary R
     * would have in some other input dictionary P which was constructed by applying modifications
     * on top of R.
     * For more information, see [InputDictRootInformation] and
     * [InputDictRootInformation.addLazyUniversalConstraint].
     */
    val modifications: PersistentList<Modification>

    /**
     * @param symbol is of KExtended sort as all the comparisons must be done on the extended key type
     */
    fun createConstraint(
        ctx: TvmContext,
        symbol: KExtended,
    ): UBoolExpr
}

data class EmptyDictConstraint(
    val condition: UBoolExpr,
    override val modifications: PersistentList<Modification>,
) : LazyUniversalQuantifierConstraint {
    override fun createConstraint(
        ctx: TvmContext,
        symbol: KExtended,
    ): UBoolExpr = ctx.mkImplies(condition, ctx.falseExpr)
}

/**
 * @param condition a condition of whether this constraint should be applied at all
 * (useful for avoiding forking)
 */
data class NotEqualConstraint(
    val value: KExtended,
    val condition: UBoolExpr,
    override val modifications: PersistentList<Modification>,
) : LazyUniversalQuantifierConstraint {
    override fun createConstraint(
        ctx: TvmContext,
        symbol: KExtended,
    ): UBoolExpr = with(ctx) { condition implies (symbol neq value) }
}

data class NextPrevQueryConstraint(
    val pivot: KExtended,
    val answer: KExtended,
    override val modifications: PersistentList<Modification>,
    val mightBeEqualToPivot: Boolean,
    val isNext: Boolean,
    val isSigned: Boolean,
) : LazyUniversalQuantifierConstraint {
    override fun createConstraint(
        ctx: TvmContext,
        symbol: KExtended,
    ): UBoolExpr {
        val pivotCmp = Cmp(isNext, !mightBeEqualToPivot, isSigned).createCmp(ctx)
        val answerCmp = (if (isNext) Cmp(CmpKind.LT) else Cmp(CmpKind.GT)).createCmp(ctx)
        return with(ctx) {
            (pivotCmp(pivot, symbol) and answerCmp(symbol, answer)).not()
        }
    }
}

data class UpperLowerBoundConstraint(
    val bound: KExtended,
    val isMax: Boolean,
    val isStrictBound: Boolean,
    val isSigned: Boolean,
    override val modifications: PersistentList<Modification>,
) : LazyUniversalQuantifierConstraint {
    override fun createConstraint(
        ctx: TvmContext,
        symbol: KExtended,
    ): UBoolExpr {
        val cmp = Cmp(isMax, isStrictBound, isSigned)
        return cmp.createCmp(ctx)(symbol, bound)
    }
}

data class EqualToOneOf(
    val values: List<GuardedKeyType>,
    override val modifications: PersistentList<Modification>,
) : LazyUniversalQuantifierConstraint {
    override fun createConstraint(
        ctx: TvmContext,
        symbol: KExtended,
    ): UBoolExpr =
        with(ctx) {
            mkOr(values.map { it.guard and (it.symbol.toExtendedKey(ctx) eq symbol) })
        }
}
