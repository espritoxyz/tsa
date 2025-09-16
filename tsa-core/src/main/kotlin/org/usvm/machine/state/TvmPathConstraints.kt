package org.usvm.machine.state

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UEqualityConstraints
import org.usvm.constraints.ULogicalConstraints
import org.usvm.constraints.UNumericConstraints
import org.usvm.constraints.UPathConstraints
import org.usvm.constraints.UTypeConstraints
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.types.TvmType
import org.usvm.merging.MutableMergeGuard
import org.usvm.model.UModelBase

class TvmPathConstraints(
    ctx: TvmContext,
    ownership: MutabilityOwnership,
    logicalConstraints: ULogicalConstraints = ULogicalConstraints.empty(),
    equalityConstraints: UEqualityConstraints = UEqualityConstraints(ctx, ownership),
    typeConstraints: UTypeConstraints<TvmType> =
        UTypeConstraints(
            ownership,
            ctx.typeSystem(),
            equalityConstraints
        ),
    numericConstraints: UNumericConstraints<UBv32Sort> = UNumericConstraints(ctx, sort = ctx.bv32Sort, ownership),
    val composers: PersistentList<UComposer<TvmType, TvmSizeSort>> = persistentListOf(),
) : UPathConstraints<TvmType>(
        ctx,
        ownership,
        logicalConstraints,
        equalityConstraints,
        typeConstraints,
        numericConstraints
    ) {
    override fun plusAssign(constraint: UBoolExpr) {
        val newConstraint = composers.compose(constraint)
        super.plusAssign(newConstraint)
    }

    fun addFixationMemory(model: UModelBase<TvmType>, values: TvmFixationMemoryValues): TvmPathConstraints {
        val clonedEqualityConstraints = equalityConstraints.clone(ownership, ownership)
        val clonedTypeConstraints = typeConstraints.clone(clonedEqualityConstraints, ownership, ownership)

//        val fixationMemory = TvmFixationMemory(ctx.tctx(), values)
        val composer = TvmFixationComposer(ctx.tctx(), values, typeConstraints, model, ownership) // UComposer(ctx.tctx(), fixationMemory, ownership)

        val newPs =
            TvmPathConstraints(
                ctx.tctx(),
                ownership,
                equalityConstraints = clonedEqualityConstraints,
                typeConstraints = clonedTypeConstraints,
                composers = composers.add(composer)
            )
        logicalAndNumericConstraints().forEach { constraint ->
            newPs += constraint
        }

        return newPs
    }

    override fun clone(
        thisOwnership: MutabilityOwnership,
        cloneOwnership: MutabilityOwnership,
    ): TvmPathConstraints {
        val clonedLogicalConstraints = logicalConstraints.clone()
        val clonedEqualityConstraints = equalityConstraints.clone(thisOwnership, cloneOwnership)
        val clonedTypeConstraints = typeConstraints.clone(clonedEqualityConstraints, thisOwnership, cloneOwnership)
        val clonedNumericConstraints = numericConstraints.clone(thisOwnership, cloneOwnership)
        this.ownership = thisOwnership
        return TvmPathConstraints(
            ctx = ctx.tctx(),
            ownership = cloneOwnership,
            logicalConstraints = clonedLogicalConstraints,
            equalityConstraints = clonedEqualityConstraints,
            typeConstraints = clonedTypeConstraints,
            numericConstraints = clonedNumericConstraints,
            composers = composers
        )
    }

    override fun mergeWith(
        other: UPathConstraints<TvmType>,
        by: MutableMergeGuard,
        thisOwnership: MutabilityOwnership,
        otherOwnership: MutabilityOwnership,
        mergedOwnership: MutabilityOwnership,
    ): UPathConstraints<TvmType>? {
        error("Merge shouldn't be called on TvmPathConstraints")
    }

    private fun logicalAndNumericConstraints(): Sequence<UBoolExpr> =
        logicalConstraints.asSequence() + numericConstraints.constraints()
}

fun <Sort : USort> List<UComposer<TvmType, TvmSizeSort>>.compose(expr: UExpr<Sort>): UExpr<Sort> {
    var newConstraint = expr
    forEach {
        newConstraint = it.compose(newConstraint)
    }
    return newConstraint
}
