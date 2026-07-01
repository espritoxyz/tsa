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
            val shouldExtend = strategies.any { it.requestMoreTime() }
            if (shouldExtend) {
                logger.info("Extended timeout by $timeStep")
                timeout = timeStatistics.runningTime + timeStep
                strategies.forEach { it.notifyAboutTimeExtension(timeout) }
            }
        }
        return timeStatistics.runningTime > timeout
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
