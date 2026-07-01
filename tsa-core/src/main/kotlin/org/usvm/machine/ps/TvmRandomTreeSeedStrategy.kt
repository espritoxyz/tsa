package org.usvm.machine.ps

import org.ton.bytecode.TvmInst
import org.usvm.PathNode
import org.usvm.UPathSelector
import org.usvm.machine.state.TvmPostProcessPhase
import org.usvm.machine.state.TvmState
import org.usvm.ps.RandomTreePathSelector
import org.usvm.statistics.UMachineObserver
import kotlin.random.Random

class TvmRandomTreeSeedStrategy(
    private val rootPathNode: PathNode<TvmInst>,
    private val shakeAfterDeaths: Int = DEFAULT_SHAKE_AFTER_DEATHS,
) : TvmSeedBasedPathSelector.SeedStrategy {
    private class Observer : UMachineObserver<TvmState> {
        val deadStatesIds = hashSetOf<UInt>()
        val terminatedStateIds = hashSetOf<UInt>()

        override fun onStateTerminated(
            state: TvmState,
            stateReachable: Boolean,
        ) {
            if (state.phase == TvmPostProcessPhase) {
                return
            }
            if (stateReachable) {
                terminatedStateIds.add(state.id)
            } else {
                deadStatesIds.add(state.id)
            }
        }
    }

    private lateinit var observer: Observer

    override fun getObserver(): UMachineObserver<TvmState> {
        observer = Observer()
        return observer
    }

    private val random = Random(0)

    private val randomTreePs = createPs()

    private fun createPs(): UPathSelector<TvmState> =
        RandomTreePathSelector.fromRoot(
            rootPathNode,
            randomNonNegativeInt = { random.nextInt(0, it) },
        )

    private var deadStatesInRow = 0

    override fun addPausedStates(states: List<TvmState>) {
        randomTreePs.add(states)
        deadStatesInRow = 0
    }

    override fun getNewSeed(extendingTime: Boolean): TvmState {
        check(!randomTreePs.isEmpty()) {
            "Cannot proceed if no states are left"
        }

        return randomTreePs.peek()
    }

    override fun updateStats(lastPeekedState: TvmState) {
        if (lastPeekedState.id in observer.deadStatesIds) {
            deadStatesInRow += 1
        }
        if (lastPeekedState.id in observer.terminatedStateIds) {
            deadStatesInRow = 0
        }
    }

    override fun shouldGetNewSeed(): Boolean = deadStatesInRow >= shakeAfterDeaths

    override fun requestMoreTime(): Boolean = false

    override fun hasAdditionalStates(): Boolean = !randomTreePs.isEmpty()

    override fun removeState(state: TvmState) {
        randomTreePs.remove(state)
    }

    companion object {
        const val DEFAULT_SHAKE_AFTER_DEATHS = 7
    }
}
