package org.usvm.machine.ps

import org.usvm.UPathSelector
import org.usvm.machine.state.C5ActionIdentifier
import org.usvm.machine.state.TvmState
import kotlin.random.Random

class TvmOutOpcodePathSelector(
    private val createBasePathSelector: (TvmState) -> UPathSelector<TvmState>,
) : UPathSelector<TvmState> {
    private val random: Random = Random(0)

    // invariant: path selectors here are not empty
    private val innerPathSelectors = mutableMapOf<Set<Int>, UPathSelector<TvmState>>()

    // set of message ids may change during step, so we need to store it
    private val stateKey = mutableMapOf<TvmState, Set<Int>>()

    override fun isEmpty(): Boolean = innerPathSelectors.isEmpty()

    override fun peek(): TvmState {
        val idx = random.nextInt(0, innerPathSelectors.size)
        return innerPathSelectors.values.toList()[idx].peek()
    }

    override fun remove(state: TvmState) {
        val key =
            stateKey[state]
                ?: error("Cannot remove state that is not in path selector")
        stateKey.remove(state)

        val ps =
            innerPathSelectors[key]
                ?: error("Expected key $key to be present")

        ps.remove(state)
        if (ps.isEmpty()) {
            innerPathSelectors.remove(key)
        }
    }

    override fun add(states: Collection<TvmState>) {
        states.forEach { state -> add(state) }
    }

    fun add(state: TvmState) {
        val oldSet =
            stateKey[state]
                ?: setOf()

        val setOfMessageIds =
            state.registersOfCurrentContract.c5.identifierList
                ?.mapNotNull {
                    (it as? C5ActionIdentifier.MsgIdentifier)?.hash()
                }?.toSet() ?: emptySet()

        val key = oldSet + setOfMessageIds
        stateKey[state] = key

        val ps =
            innerPathSelectors[key]?.also {
                it.add(listOf(state))
            } ?: run {
                createBasePathSelector(state)
            }

        innerPathSelectors[key] = ps
    }

    override fun update(state: TvmState) {
        remove(state)
        add(state)
    }
}
