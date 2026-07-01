package org.usvm.machine.ps

import mu.KLogging
import org.ton.bytecode.TvmRealInst
import org.ton.disasm.TvmPhysicalInstLocation
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.lastStmt
import org.usvm.statistics.UMachineObserver

class TvmUncoveredInstSeedStrategy(
    private val observer: Observer,
) : TvmSeedBasedPathSelector.SeedStrategy {
    class Observer : UMachineObserver<TvmState> {
        val visitedInsts = hashSetOf<TvmPhysicalInstLocation>()
        val deadStateIds = mutableSetOf<UInt>()

        override fun onStatePeeked(state: TvmState) {
            (state.lastStmt as? TvmRealInst)?.physicalLocation?.let { visitedInsts.add(it) }
        }

        override fun onStateTerminated(
            state: TvmState,
            stateReachable: Boolean,
        ) {
            if (!stateReachable) {
                deadStateIds.add(state.id)
            }
        }
    }

    private val stmtToState = mutableSetOf<Pair<TvmPhysicalInstLocation, TvmState>>()

    override fun shouldGetNewSeed(): Boolean = false

    private fun updateStats() {
        stmtToState.removeIf {
            check(it.second.lastRealStmt?.physicalLocation == it.first) {
                "Inconsistent state in [TvmUncoveredInstShakingStrategy]"
            }
            it.first in observer.visitedInsts
        }
    }

    override fun requestMoreTime(): Boolean {
        updateStats()
        return stmtToState.isNotEmpty()
    }

    override fun addPausedStates(states: List<TvmState>) {
        states.forEach { addStateIfNewInst(it) }
    }

    override fun getNewSeed(extendingTime: Boolean): TvmState {
        updateStats()
        check(stmtToState.isNotEmpty()) {
            "No states left"
        }
        val result = stmtToState.first()
        logger.info {
            "Getting seed with uncovered inst (${result.first.cellHashHex}, ${result.first.offset})"
        }
        return result.second
    }

    override fun hasAdditionalStates(): Boolean {
        updateStats()
        return stmtToState.isNotEmpty()
    }

    private fun addStateIfNewInst(state: TvmState) {
        val loc =
            (state.lastStmt as? TvmRealInst)?.physicalLocation
                ?: return
        if (loc !in observer.visitedInsts) {
            stmtToState.add(loc to state)
        }
    }

    override fun updateStats(lastPeekedState: TvmState) {
        val oldElem = stmtToState.firstOrNull { it.second == lastPeekedState }
        check(oldElem == null) {
            "Expected state ${lastPeekedState.id} to be out of [TvmUncoveredInstSeedStrategy]"
        }
    }

    override fun removeState(state: TvmState) {
        stmtToState.removeIf { it.second == state }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
