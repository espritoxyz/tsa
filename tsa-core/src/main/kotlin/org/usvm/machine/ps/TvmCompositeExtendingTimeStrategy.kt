package org.usvm.machine.ps

import mu.KLogging
import org.ton.bytecode.TvmCodeBlock
import org.usvm.machine.state.TvmState
import org.usvm.statistics.TimeStatistics
import org.usvm.stopstrategies.StopStrategy
import kotlin.time.Duration

class TvmCompositeExtendingTimeStrategy(
    private val timeStatistics: TimeStatistics<TvmCodeBlock, TvmState>,
    private var timeout: Duration,
    private val timeStep: Duration,
) : StopStrategy {
    private val strategies = mutableListOf<TvmExtendingTimeStrategy>()

    fun addExtendingTimeStrategy(extendingTimeStrategy: TvmExtendingTimeStrategy) {
        strategies += extendingTimeStrategy
    }

    override fun shouldStop(): Boolean {
        if (timeStatistics.runningTime > timeout) {
            val results = strategies.map { it.requestMoreTime() }
            if (TvmExtendingTimeStrategy.TimeExtension.TIME_STEP in results) {
                logger.info("Extended timeout by $timeStep")
                timeout = timeStatistics.runningTime + timeStep
                strategies.forEach { it.notifyAboutTimeExtension(timeout) }
            } else if (TvmExtendingTimeStrategy.TimeExtension.ONE_STEP in results) {
                return false
            }
        }
        return timeStatistics.runningTime > timeout
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
