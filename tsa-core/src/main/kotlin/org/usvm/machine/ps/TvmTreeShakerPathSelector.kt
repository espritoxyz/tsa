package org.usvm.machine.ps

import org.ton.bytecode.TvmInst
import org.usvm.UPathSelector
import org.usvm.machine.state.TvmState
import org.usvm.ps.DfsPathSelector
import org.usvm.ps.RandomTreePathSelector
import org.usvm.statistics.UMachineObserver
import kotlin.random.Random

class TvmTreeShakerPathSelector(
    initialState: TvmState,
    private val observer: Observer,
    private val shakeAfterDeaths: Int = DEFAULT_SHAKE_AFTER_DEATHS,
) : UPathSelector<TvmState>,
    UMachineObserver<TvmState> {
    class Observer : UMachineObserver<TvmState> {
        val deadStatesIds = hashSetOf<UInt>()
        val terminatedStateIds = hashSetOf<UInt>()

        override fun onStateTerminated(
            state: TvmState,
            stateReachable: Boolean,
        ) {
            if (stateReachable) {
                terminatedStateIds.add(state.id)
            } else {
                deadStatesIds.add(state.id)
            }
        }
    }

    private val random = Random(0)

    private val randomTreePs =
        RandomTreePathSelector.fromRoot<TvmState, TvmInst>(
            initialState.pathNode,
            randomNonNegativeInt = { random.nextInt(0, it) },
        )

    private var basePathSelector = createBasePathSelector()

    private var deadStatesInRow = 0

    private fun updateBasePathSelector() {
        while (!basePathSelector.isEmpty()) {
            val state = basePathSelector.peek()
            randomTreePs.add(listOf(state))
            basePathSelector.remove(state)
        }

        check(!randomTreePs.isEmpty()) {
            "Cannot proceed if no states are left"
        }

        val state = randomTreePs.peek()
        randomTreePs.remove(state)

        basePathSelector = createBasePathSelector()
        basePathSelector.add(listOf(state))

        deadStatesInRow = 0
    }

    override fun isEmpty(): Boolean = basePathSelector.isEmpty() && randomTreePs.isEmpty()

    private var lastPeekedState: TvmState? = null

    private fun updateStats() {
        val state =
            lastPeekedState
                ?: return
        if (state.id in observer.deadStatesIds) {
            deadStatesInRow += 1
        }
        if (state.id in observer.terminatedStateIds) {
            deadStatesInRow = 0
        }
    }

    override fun peek(): TvmState {
        updateStats()

        if (basePathSelector.isEmpty() || deadStatesInRow >= shakeAfterDeaths) {
            updateBasePathSelector()
        }

        return basePathSelector.peek().also {
            lastPeekedState = it
        }
    }

    override fun remove(state: TvmState) {
        basePathSelector.remove(state)
    }

    override fun add(states: Collection<TvmState>) {
        basePathSelector.add(states)
    }

    override fun update(state: TvmState) {
        basePathSelector.update(state)
    }

    companion object {
        const val DEFAULT_SHAKE_AFTER_DEATHS = 5

        private fun createBasePathSelector(): UPathSelector<TvmState> = DfsPathSelector()
    }
}
