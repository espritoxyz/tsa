package org.usvm.machine.ps

import org.usvm.machine.state.TvmState
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.UMachineObserver

class TvmCompositeSeedStrategy(
    private val strategies: List<TvmSeedBasedPathSelector.SeedStrategy>,
) : TvmSeedBasedPathSelector.SeedStrategy {
    override fun getObserver(): UMachineObserver<TvmState> =
        CompositeUMachineObserver(strategies.map { it.getObserver() })

    override fun requestMoreTime(): Boolean =
        strategies
            .map {
                it.requestMoreTime()
            }.reduce { acc, bool -> acc || bool }

    override fun shouldShake(): Boolean =
        strategies
            .map {
                it.shouldShake()
            }.reduce { acc, bool -> acc || bool }

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
                    it.shouldShake()
                } ?: let {
                    strategies.first {
                        it.hasAdditionalStates()
                    }
                }
            strategy.getNewSeed(extendingTime = false)
        }

    override fun hasAdditionalStates(): Boolean =
        strategies
            .map {
                it.hasAdditionalStates()
            }.reduce { acc, bool -> acc || bool }

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
