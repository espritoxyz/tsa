package org.usvm.machine.ps

import mu.KLogging
import org.ton.bytecode.TsaArtificialInst
import org.ton.bytecode.TvmCodeBlock
import org.usvm.UPathSelector
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.lastStmt
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.UMachineObserver
import org.usvm.stopstrategies.StopStrategy
import kotlin.time.Duration

class TvmShakerPathSelector(
    private val strategy: ShakingStrategy,
    private val timeStatistics: TimeStatistics<TvmCodeBlock, TvmState>,
    private var currentTimeout: Duration,
    private val timeStep: Duration? = null,
    private val createDfsLikePathSelector: () -> UPathSelector<TvmState>,
) : UPathSelector<TvmState>,
    StopStrategy {
    var basePathSelector = createDfsLikePathSelector()
    private val makeOneStepFor = mutableSetOf<TvmState>()

    interface ShakingStrategy {
        /**
         * Return new seed for DFS path selector
         * */
        fun shake(extendingTime: Boolean): TvmState

        fun getObserver(): UMachineObserver<TvmState>

        fun requestMoreTime(): Boolean

        fun shouldShake(): Boolean

        fun addPausedStates(states: List<TvmState>)

        fun hasAdditionalStates(): Boolean

        fun updateStats(lastPeekedState: TvmState)

        fun removeState(state: TvmState)
    }

    override fun isEmpty(): Boolean =
        basePathSelector.isEmpty() && !strategy.hasAdditionalStates() && makeOneStepFor.isEmpty()

    private var lastPeekedState: TvmState? = null
    private var lastPeekedStateWasFromBasePs = false

    private fun extractStatesFromBasePS(): List<TvmState> {
        val result = mutableListOf<TvmState>()
        while (!basePathSelector.isEmpty()) {
            result.add(basePathSelector.peek())
            basePathSelector.remove(result.last())
        }
        return result
    }

    private fun shake(extendingTime: Boolean) {
        strategy.addPausedStates(extractStatesFromBasePS())
        val seed = strategy.shake(extendingTime = extendingTime)
        basePathSelector = createDfsLikePathSelector().also { it.add(listOf(seed)) }
        strategy.removeState(seed)
    }

    override fun peek(): TvmState {
        val state = lastPeekedState
        if (state != null) {
            strategy.updateStats(state)
        }

        if (makeOneStepFor.isNotEmpty()) {
            val result = makeOneStepFor.first()
            lastPeekedState = result
            lastPeekedStateWasFromBasePs = false
            return result
        }

        if (basePathSelector.isEmpty() || strategy.shouldShake()) {
            shake(extendingTime = false)
        }

        return basePathSelector.peek().also {
            lastPeekedState = it
            lastPeekedStateWasFromBasePs = true
        }
    }

    override fun remove(state: TvmState) {
        if (lastPeekedStateWasFromBasePs) {
            basePathSelector.remove(state)
        } else {
            check(state in makeOneStepFor)
            makeOneStepFor.remove(state)
        }
    }

    override fun add(states: Collection<TvmState>) {
        states.forEach { add(it) }
    }

    fun add(state: TvmState) {
        if (state.lastStmt is TsaArtificialInst) {
            makeOneStepFor += state
        } else {
            basePathSelector.add(listOf(state))
        }
    }

    override fun update(state: TvmState) {
        if (lastPeekedStateWasFromBasePs) {
            basePathSelector.update(state)
        } else if (state.lastStmt !is TsaArtificialInst) {
            check(state in makeOneStepFor)
            makeOneStepFor.remove(state)
            strategy.addPausedStates(listOf(state))
        } else {
            check(state in makeOneStepFor)
            // just keep it there
        }
    }

    override fun shouldStop(): Boolean {
        if (makeOneStepFor.isNotEmpty()) {
            return false
        }
        if (timeStatistics.runningTime > currentTimeout && timeStep != null && strategy.requestMoreTime()) {
            logger.info("Extended timeout by $timeStep")
            currentTimeout = timeStatistics.runningTime + timeStep
            shake(extendingTime = true)
        }
        return timeStatistics.runningTime > currentTimeout
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
