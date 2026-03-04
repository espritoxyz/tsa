package org.usvm.machine.state

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UContext
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UEqualityConstraints
import org.usvm.constraints.ULogicalConstraints
import org.usvm.constraints.UNumericConstraints
import org.usvm.constraints.UPathConstraints
import org.usvm.constraints.UTypeConstraints
import org.usvm.machine.types.TvmType

class TvmPathConstraints(
    ctx: UContext<*>,
    override var ownership: MutabilityOwnership,
    logicalConstraints: ULogicalConstraints = ULogicalConstraints.empty(),
    equalityConstraints: UEqualityConstraints = UEqualityConstraints(ctx, ownership),
    typeConstraints: UTypeConstraints<TvmType> =
        UTypeConstraints(
            ownership,
            ctx.typeSystem(),
            equalityConstraints,
        ),
    numericConstraints: UNumericConstraints<UBv32Sort> =
        UNumericConstraints(ctx, sort = ctx.bv32Sort, ownership = ownership),
    var tvmSoftConstraints: PersistentSet<UBoolExpr> = persistentSetOf(),
) : UPathConstraints<TvmType>(
        ctx,
        ownership,
        logicalConstraints,
        equalityConstraints,
        typeConstraints,
        numericConstraints,
    ) {
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
            ctx = ctx,
            logicalConstraints = clonedLogicalConstraints,
            equalityConstraints = clonedEqualityConstraints,
            typeConstraints = clonedTypeConstraints,
            numericConstraints = clonedNumericConstraints,
            ownership = cloneOwnership,
            tvmSoftConstraints = tvmSoftConstraints,
        )
    }
}

fun TvmState.addSoftConstraints(constraint: UBoolExpr) {
    val ps =
        pathConstraints as? TvmPathConstraints
            ?: error("Unexpected constraints: $pathConstraints")
    ps.tvmSoftConstraints = ps.tvmSoftConstraints.add(constraint)
}
