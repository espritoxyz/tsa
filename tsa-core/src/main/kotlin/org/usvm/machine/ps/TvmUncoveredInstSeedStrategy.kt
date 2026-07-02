package org.usvm.machine.ps

import mu.KLogging
import org.ton.bytecode.TvmRealInst
import org.ton.disasm.TvmPhysicalInstLocation
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.lastStmt
import org.usvm.statistics.UMachineObserver

class TvmUncoveredInstSeedStrategy<Key>(
    private val observer: Observer<Key>,
) : TvmSeedBasedPathSelector.SeedStrategy {
    class Observer<Key>(
        val getStateKey: (TvmState) -> Key,
    ) : UMachineObserver<TvmState> {
        val visitedInsts = hashMapOf<Key, MutableSet<TvmPhysicalInstLocation>>()

        override fun onStatePeeked(state: TvmState) {
            val key = getStateKey(state)
            (state.lastStmt as? TvmRealInst)?.physicalLocation?.let {
                val set = visitedInsts.getOrPut(key) { hashSetOf() }
                set.add(it)
            }
        }
    }

    private val stmtToState = mutableSetOf<Triple<Key, TvmPhysicalInstLocation, TvmState>>()

    override fun shouldGetNewSeed(): Boolean = false

    private fun updateStats() {
        stmtToState.removeIf { (key, loc, state) ->
            check(state.lastRealStmt?.physicalLocation == loc && observer.getStateKey(state) == key) {
                "Inconsistent state in [TvmUncoveredInstSeedStrategy]"
            }
            loc in observer.visitedInsts.getOrDefault(key, setOf())
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
        val (key, loc, state) = stmtToState.first()
        logger.info {
            "Getting seed with uncovered with key $key inst (${loc.cellHashHex}, ${loc.offset})"
        }
        return state
    }

    override fun hasAdditionalStates(): Boolean {
        updateStats()
        return stmtToState.isNotEmpty()
    }

    private fun addStateIfNewInst(state: TvmState) {
        val loc =
            (state.lastStmt as? TvmRealInst)?.physicalLocation
                ?: return
        val key = observer.getStateKey(state)
        if (loc !in observer.visitedInsts.getOrDefault(key, setOf())) {
            stmtToState.add(Triple(key, loc, state))
        }
    }

    override fun updateStats(lastPeekedState: TvmState) {
        val oldElem = stmtToState.firstOrNull { it.third.id == lastPeekedState.id }
        check(oldElem == null) {
            "Expected state ${lastPeekedState.id} to be out of [TvmUncoveredInstSeedStrategy]"
        }
    }

    override fun removeState(state: TvmState) {
        stmtToState.removeIf { it.third.id == state.id }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
