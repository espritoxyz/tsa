package org.usvm.machine.ps

import org.usvm.machine.state.TvmState
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.UMachineObserver

class TvmCompositeSeedStrategy(
    private val strategies: List<TvmSeedBasedPathSelector.SeedStrategy>,
) : TvmSeedBasedPathSelector.SeedStrategy {
    override fun getObserver(): UMachineObserver<TvmState> =
        CompositeUMachineObserver(strategies.map { it.getObserver() })

    override fun requestMoreTime(): Boolean = strategies.any { it.requestMoreTime() }

    override fun shouldGetNewSeed(): Boolean = strategies.any { it.shouldGetNewSeed() }

    override fun addPausedStates(states: List<TvmState>) {
        strategies.forEach {
            it.addPausedStates(states)
        }
    }

    override fun getNewSeed(extendingTime: Boolean): TvmState =
        if (extendingTime) {
            strategies
                .first {
                    it.requestMoreTime()
                }.getNewSeed(extendingTime = true)
        } else {
            val strategy =
                strategies.firstOrNull {
                    it.shouldGetNewSeed()
                } ?: let {
                    strategies.first {
                        it.hasAdditionalStates()
                    }
                }
            strategy.getNewSeed(extendingTime = false)
        }

    override fun hasAdditionalStates(): Boolean = strategies.any { it.hasAdditionalStates() }

    override fun updateStats(lastPeekedState: TvmState) {
        strategies.forEach {
            it.updateStats(lastPeekedState)
        }
    }

    override fun removeState(state: TvmState) {
        strategies.forEach {
            it.removeState(state)
        }
    }
}
