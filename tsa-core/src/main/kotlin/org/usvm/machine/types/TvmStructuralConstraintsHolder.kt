package org.usvm.machine.types

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import org.usvm.UBoolExpr
import org.usvm.isTrue
import org.usvm.machine.TvmStepScopeManager

class TvmStructuralConstraintsHolder(
    private var constraints: PersistentSet<UBoolExpr> = persistentSetOf(),
) {
    fun add(constraint: UBoolExpr): TvmStructuralConstraintsHolder {
        if (constraint.isTrue) {
            return this
        }

        return TvmStructuralConstraintsHolder(constraints.add(constraint))
    }

    fun applyTo(stepScope: TvmStepScopeManager): Unit? {
        // TODO: memorize already applied constraints
        var result: Unit? = Unit
        constraints.forEach {
            // we might want to apply structural constraints to forked states (with erroneous paths)
            // even if we do not have a valid curState
            if (stepScope.canBeProcessed) {
                stepScope.assert(it) ?: run {
                    result = null
                }
            }
            stepScope.filterForkedStatesOnCondition(it)
        }
        return result
    }
}
