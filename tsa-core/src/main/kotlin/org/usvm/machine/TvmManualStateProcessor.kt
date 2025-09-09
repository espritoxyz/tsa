package org.usvm.machine

import org.usvm.machine.state.TvmState

open class TvmManualStateProcessor {
    open fun postProcessBeforePartialConcretization(state: TvmState): List<TvmState> = listOf(state)

    open fun postProcessAfterPartialConcretization(state: TvmState): List<TvmState> = listOf(state)
}
