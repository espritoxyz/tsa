package org.usvm.machine.ps

import org.usvm.UPathSelector
import org.usvm.machine.state.SemanticPriority
import org.usvm.machine.state.TvmState

class TvmSemanticPriorityPathSelector(
    createPathSelector: () -> UPathSelector<TvmState>,
) : UPathSelector<TvmState> {
    private val innerPathSelector = createPathSelector()
    private val lowPriorityStatesPs = createPathSelector()

    override fun isEmpty(): Boolean = innerPathSelector.isEmpty() && lowPriorityStatesPs.isEmpty()

    private fun putLowPriorityStateInMainPs() {
        require(!lowPriorityStatesPs.isEmpty()) {
            "Low priority PS must not be empty"
        }

        val state = lowPriorityStatesPs.peek()
        lowPriorityStatesPs.remove(state)
        innerPathSelector.add(listOf(state))
    }

    override fun peek(): TvmState {
        if (innerPathSelector.isEmpty()) {
            putLowPriorityStateInMainPs()
        }
        return innerPathSelector.peek()
    }

    override fun remove(state: TvmState) {
        innerPathSelector.remove(state)
    }

    override fun add(states: Collection<TvmState>) {
        states.forEach {
            add(it)
        }
    }

    fun add(state: TvmState) {
        when (state.semanticPriority) {
            SemanticPriority.NORMAL -> innerPathSelector.add(listOf(state))
            SemanticPriority.LOW -> lowPriorityStatesPs.add(listOf(state))
        }
    }

    override fun update(state: TvmState) {
        remove(state)
        add(state)
    }
}
