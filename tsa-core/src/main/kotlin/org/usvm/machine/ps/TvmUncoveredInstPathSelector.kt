package org.usvm.machine.ps

import org.ton.bytecode.TvmRealInst
import org.ton.disasm.TvmPhysicalInstLocation
import org.usvm.UPathSelector
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.lastStmt
import org.usvm.statistics.UMachineObserver

class TvmUncoveredInstPathSelector(
    private val basePathSelector: UPathSelector<TvmState>,
    private val observer: Observer,
) : UPathSelector<TvmState> {
    class Observer : UMachineObserver<TvmState> {
        val visitedInsts = hashSetOf<TvmPhysicalInstLocation>()

        override fun onStatePeeked(state: TvmState) {
            (state.lastStmt as? TvmRealInst)?.physicalLocation?.let {
                visitedInsts.add(it)
            }
        }
    }

    private val statesWithUnvisitedInsts = linkedSetOf<TvmState>()

    override fun isEmpty(): Boolean = statesWithUnvisitedInsts.isEmpty() && basePathSelector.isEmpty()

    private fun uncoveredInst(state: TvmState): Boolean =
        (state.lastStmt as? TvmRealInst)?.physicalLocation?.let {
            it !in observer.visitedInsts
        } == true

    private fun update() {
        statesWithUnvisitedInsts.removeIf { state ->
            val toRemove = !uncoveredInst(state)
            if (toRemove) {
                basePathSelector.add(listOf(state))
            }
            toRemove
        }
    }

    override fun peek(): TvmState {
        update()
        if (statesWithUnvisitedInsts.isNotEmpty()) {
            return statesWithUnvisitedInsts.first()
        }
        return basePathSelector.peek()
    }

    override fun remove(state: TvmState) {
        val removed = statesWithUnvisitedInsts.remove(state)
        if (!removed) {
            basePathSelector.remove(state)
        }
    }

    override fun add(states: Collection<TvmState>) {
        states.forEach { add(it) }
    }

    fun add(state: TvmState) {
        if (uncoveredInst(state)) {
            statesWithUnvisitedInsts.add(state)
        } else {
            basePathSelector.add(listOf(state))
        }
    }

    override fun update(state: TvmState) {
        remove(state)
        add(state)
    }
}
