package org.usvm.machine

import org.usvm.machine.state.TvmMethodResult
import org.usvm.machine.state.TvmState
import org.usvm.statistics.UMachineObserver
import org.usvm.stopstrategies.StopStrategy

interface TvmAdditionalStopStrategy :
    StopStrategy,
    UMachineObserver<TvmState>

data object NoAdditionalStopStrategy : TvmAdditionalStopStrategy {
    override fun shouldStop(): Boolean = false
}

data class ExploreExitCodesStopStrategy(
    val exitCodesToFind: Set<Int>,
) : TvmAdditionalStopStrategy {
    val foundExitCodes = hashSetOf<Int>()

    override fun onStateTerminated(
        state: TvmState,
        stateReachable: Boolean,
    ) {
        if (!stateReachable) {
            return
        }

        val result = state.methodResult
        if (result is TvmMethodResult.TvmFailure) {
            foundExitCodes += result.exit.exitCode
        }
    }

    override fun shouldStop(): Boolean = exitCodesToFind.all { it in foundExitCodes }
}
