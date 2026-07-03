package org.usvm.machine.ps

import org.usvm.machine.state.TvmState
import org.usvm.statistics.UMachineObserver

class TvmExtendableMachineObserver : UMachineObserver<TvmState> {
    private val observers = mutableListOf<UMachineObserver<TvmState>>()

    fun addObserver(observer: UMachineObserver<TvmState>) {
        observers += observer
    }

    override fun onMachineStarted() {
        observers.forEach { it.onMachineStarted() }
    }

    override fun onMachineStopped() {
        observers.forEach { it.onMachineStopped() }
    }

    override fun onStateTerminated(
        state: TvmState,
        stateReachable: Boolean,
    ) {
        observers.forEach { it.onStateTerminated(state, stateReachable) }
    }

    override fun onState(
        parent: TvmState,
        forks: Sequence<TvmState>,
    ) {
        observers.forEach { it.onState(parent, forks) }
    }

    override fun onStatePeeked(state: TvmState) {
        observers.forEach { it.onStatePeeked(state) }
    }
}
