package org.usvm.machine.state

import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.types.TvmType

class TvmPathConstraints(
    ctx: TvmContext,
    ownership: MutabilityOwnership,
    private val composer: UComposer<TvmType, TvmSizeSort>,
) : UPathConstraints<TvmType>(ctx, ownership) {
    override operator fun plusAssign(constraint: UBoolExpr) {
        val composedConstraint = composer.compose(constraint)
        super.plusAssign(composedConstraint)
    }
}
