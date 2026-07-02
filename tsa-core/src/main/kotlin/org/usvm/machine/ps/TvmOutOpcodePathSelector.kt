package org.usvm.machine.ps

import mu.KLogging
import org.usvm.UPathSelector
import org.usvm.machine.state.C5ActionIdentifier
import org.usvm.machine.state.TvmState
import kotlin.random.Random

class TvmOutOpcodePathSelector(
    private val createBasePathSelector: () -> UPathSelector<TvmState>,
) : UPathSelector<TvmState> {
    private val random: Random = Random(0)

    // invariant: path selectors here are not empty
    private val innerPathSelectors = linkedMapOf<Set<Int>, UPathSelector<TvmState>>()

    // set of message ids may change during step, so we need to store it
    private val stateKey = mutableMapOf<TvmState, Set<Int>>()

    override fun isEmpty(): Boolean = innerPathSelectors.isEmpty()

    override fun peek(): TvmState {
        val keys = innerPathSelectors.keys.toList()
        val idx = random.nextInt(0, innerPathSelectors.size)
        val key = keys[idx]
        val ps =
            innerPathSelectors[key]
                ?: error("Path selector $key not found")

        logger.debug {
            "Chose key $key for the step"
        }

        return ps.peek()
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
        val key = getStateKey(state)
        stateKey[state] = key

        val ps =
            innerPathSelectors[key]?.also {
                it.add(listOf(state))
            } ?: run {
                createBasePathSelector().also {
                    it.add(listOf(state))
                }
            }

        innerPathSelectors[key] = ps
    }

    override fun update(state: TvmState) {
        remove(state)
        add(state)
    }

    companion object {
        fun getStateKey(state: TvmState): Set<Int> {
            val setOfMessageIds =
                state.c5IdentifierList.values
                    .flatMap { it.toList() }
                    .mapNotNull {
                        (it as? C5ActionIdentifier.MsgIdentifier)?.hash()
                    }.toSet()

            return setOfMessageIds
        }

        private val logger = object : KLogging() {}.logger
    }
}
