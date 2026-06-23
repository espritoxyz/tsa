package org.usvm.machine.ps

import org.ton.bytecode.TvmRealInst
import org.ton.disasm.TvmPhysicalInstLocation
import org.usvm.UPathSelector
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.lastStmt
import org.usvm.statistics.UMachineObserver

class TvmUncoveredInstPathSelector(
    private val psWithUncoveredInsts: UPathSelector<TvmState>,
    private val fallbackPs: UPathSelector<TvmState>,
    private val observer: Observer,
) : UPathSelector<TvmState> {
    class Observer : UMachineObserver<TvmState> {
        val visitedInsts = hashSetOf<TvmPhysicalInstLocation>()

        override fun onStatePeeked(state: TvmState) {
            (state.lastStmt as? TvmRealInst)?.physicalLocation?.let { visitedInsts.add(it) }
        }
    }

    private val stateLocation = hashMapOf<TvmState, UPathSelector<TvmState>>()

    override fun isEmpty(): Boolean = fallbackPs.isEmpty() && psWithUncoveredInsts.isEmpty()

    override fun peek(): TvmState {
        while (!psWithUncoveredInsts.isEmpty()) {
            val state = psWithUncoveredInsts.peek()
            val stmt =
                state.lastStmt as? TvmRealInst
                    ?: error("Unexpected next instruction in statesWithUncoveredSuccessors")
            if (stmt.physicalLocation !in observer.visitedInsts) {
                return state
            }
            fallbackPs.add(listOf(state))
            psWithUncoveredInsts.remove(state)
            stateLocation[state] = fallbackPs
        }
        return fallbackPs.peek()
    }

    override fun remove(state: TvmState) {
        val loc =
            stateLocation[state]
                ?: error("$state is not in path selector")
        loc.remove(state)
        stateLocation.remove(state)
    }

    override fun add(states: Collection<TvmState>) {
        states.forEach { add(it) }
    }

    fun add(state: TvmState) {
        val stmt = state.lastStmt as? TvmRealInst
        if (stmt == null || stmt.physicalLocation in observer.visitedInsts) {
            fallbackPs.add(listOf(state))
            stateLocation[state] = fallbackPs
        } else {
            psWithUncoveredInsts.add(listOf(state))
            stateLocation[state] = psWithUncoveredInsts
        }
    }

    override fun update(state: TvmState) {
        remove(state)
        add(state)
    }
}
