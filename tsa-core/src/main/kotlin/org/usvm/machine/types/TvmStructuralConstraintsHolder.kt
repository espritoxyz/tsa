package org.usvm.machine.types

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import org.usvm.UBoolExpr
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.isTrue
import org.usvm.machine.TvmStepScopeManager

class TvmStructuralConstraintsHolder(
    private val constraints: PersistentSet<UBoolExpr> = persistentSetOf(),
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
            // we want to apply structural constraints to forked states (with erroneous paths)
            // (hack for this)
            val newStepScope = TvmStepScopeManager(
                stepScope.getOriginalStateWithoutDeathCheck(),
                UForkBlackList.createDefault(),
                allowFailuresOnCurrentStep = true,
            )
            newStepScope.assert(it) ?: run {
                stepScope.assert(stepScope.ctx.falseExpr)
                result = null
            }
            stepScope.filterForkedStatesOnCondition(it)
        }
        return result
    }
}
