package org.usvm.machine.ps

import org.usvm.machine.state.TvmState
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.UMachineObserver

class TvmCompositeShakingStrategy(
    private val strategies: List<TvmShakerPathSelector.ShakingStrategy>,
) : TvmShakerPathSelector.ShakingStrategy {
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

    override fun shake(extendingTime: Boolean): TvmState =
        if (extendingTime) {
            strategies
                .first {
                    it.requestMoreTime()
                }.shake(extendingTime = true)
        } else {
            val strategy =
                strategies.firstOrNull {
                    it.shouldShake()
                } ?: let {
                    strategies.first {
                        it.hasAdditionalStates()
                    }
                }
            strategy.shake(extendingTime = false)
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
