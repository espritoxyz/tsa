package org.usvm.machine.ps

import org.usvm.machine.state.TvmState

class TvmCompositeSeedStrategy(
    private val strategies: List<TvmSeedBasedPathSelector.SeedStrategy>,
) : TvmSeedBasedPathSelector.SeedStrategy {
    var lastStrategy = -1

    override fun requestMoreTime(): Boolean = strategies.any { it.requestMoreTime() }

    override fun shouldGetNewSeed(): Boolean = strategies.any { it.shouldGetNewSeed() }

    override fun addPausedStates(states: List<TvmState>) {
        strategies.forEach {
            it.addPausedStates(states)
        }
    }

    override fun getNewSeed(extendingTime: Boolean): TvmState {
        var strategy: TvmSeedBasedPathSelector.SeedStrategy? = null
        if (extendingTime) {
            strategy =
                strategies.firstOrNull {
                    it.requestMoreTime()
                }
        }
        strategy = strategy ?: strategies.firstOrNull {
            it.shouldGetNewSeed()
        } ?: let {
            strategies.first {
                it.hasAdditionalStates()
            }
        }

        lastStrategy = strategies.indexOf(strategy)

        return strategy.getNewSeed(extendingTime = false)
    }

    override fun hasAdditionalStates(): Boolean = strategies.any { it.hasAdditionalStates() }

    override fun updateStats(lastPeekedState: TvmState) {
        if (lastStrategy != -1) {
            strategies[lastStrategy].updateStats(lastPeekedState)
        }
    }

    override fun removeState(state: TvmState) {
        strategies.forEach {
            it.removeState(state)
        }
    }
}
