package org.usvm.machine.state

import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UEqualityConstraints
import org.usvm.constraints.ULogicalConstraints
import org.usvm.constraints.UNumericConstraints
import org.usvm.constraints.UPathConstraints
import org.usvm.constraints.UTypeConstraints
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.types.TvmType
import org.usvm.merging.MutableMergeGuard

class TvmPathConstraints(
    ctx: TvmContext,
    ownership: MutabilityOwnership,
    logicalConstraints: ULogicalConstraints = ULogicalConstraints.empty(),
    equalityConstraints: UEqualityConstraints = UEqualityConstraints(ctx, ownership),
    typeConstraints: UTypeConstraints<TvmType> = UTypeConstraints(ownership, ctx.typeSystem(), equalityConstraints),
    numericConstraints: UNumericConstraints<UBv32Sort> = UNumericConstraints(ctx, sort = ctx.bv32Sort, ownership)
) : UPathConstraints<TvmType>(
    ctx,
    ownership,
    logicalConstraints,
    equalityConstraints,
    typeConstraints,
    numericConstraints,
) {
    override fun plusAssign(constraint: UBoolExpr) {
        super.plusAssign(constraint)
    }

    override fun mergeWith(
        other: UPathConstraints<TvmType>,
        by: MutableMergeGuard,
        thisOwnership: MutabilityOwnership,
        otherOwnership: MutabilityOwnership,
        mergedOwnership: MutabilityOwnership
    ): UPathConstraints<TvmType>? {
        error("mergeWith shouldn't be called on TvmPathConstraints")
    }

    override fun clone(
        thisOwnership: MutabilityOwnership,
        cloneOwnership: MutabilityOwnership
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
        )
    }
}
