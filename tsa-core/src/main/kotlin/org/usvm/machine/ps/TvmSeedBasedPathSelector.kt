package org.usvm.machine.ps

import mu.KLogging
import org.ton.bytecode.TsaArtificialInst
import org.usvm.UPathSelector
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.lastStmt
import kotlin.time.Duration

class TvmSeedBasedPathSelector(
    private val strategy: SeedStrategy,
    private val canExtendTime: Boolean,
    private val createDfsLikePathSelector: () -> UPathSelector<TvmState>,
) : UPathSelector<TvmState>,
    TvmExtendingTimeStrategy {
    var basePathSelector = createDfsLikePathSelector()
    private val makeOneStepFor = mutableSetOf<TvmState>()

    interface SeedStrategy {
        fun getNewSeed(extendingTime: Boolean): TvmState

        fun requestMoreTime(): Boolean

        fun shouldGetNewSeed(): Boolean

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

    private fun changeSeed(extendingTime: Boolean) {
        logger.debug("Getting new seed")

        strategy.addPausedStates(extractStatesFromBasePS())
        val seed = strategy.getNewSeed(extendingTime = extendingTime)
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

        if (basePathSelector.isEmpty() || strategy.shouldGetNewSeed()) {
            changeSeed(extendingTime = false)
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
        if (forceOneMoreStep(state)) {
            makeOneStepFor += state
        } else {
            basePathSelector.add(listOf(state))
        }
    }

    override fun update(state: TvmState) {
        if (lastPeekedStateWasFromBasePs) {
            basePathSelector.update(state)
        } else if (!forceOneMoreStep(state)) {
            check(state in makeOneStepFor)
            makeOneStepFor.remove(state)
            strategy.addPausedStates(listOf(state))
        } else {
            check(state in makeOneStepFor)
            // just keep it there
        }
    }

    private fun forceOneMoreStep(state: TvmState): Boolean = state.lastStmt is TsaArtificialInst && canExtendTime

    override fun requestMoreTime(): TvmExtendingTimeStrategy.TimeExtension {
        if (makeOneStepFor.isNotEmpty()) {
            return TvmExtendingTimeStrategy.TimeExtension.ONE_STEP
        }

        strategy.addPausedStates(extractStatesFromBasePS())
        val strategyRequest = strategy.requestMoreTime()
        return if (strategyRequest) {
            TvmExtendingTimeStrategy.TimeExtension.TIME_STEP
        } else {
            TvmExtendingTimeStrategy.TimeExtension.NONE
        }
    }

    override fun notifyAboutTimeExtension(newTimeout: Duration) {
        if (strategy.hasAdditionalStates()) {
            changeSeed(extendingTime = true)
        }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
