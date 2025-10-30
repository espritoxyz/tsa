package org.usvm.machine.statistics

import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmRealInst
import org.ton.disasm.TvmPhysicalInstLocation
import org.usvm.StateId
import org.usvm.logger
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.lastStmt
import org.usvm.statistics.UMachineObserver

class StateHistoryGraph : UMachineObserver<TvmState> {
    sealed interface Node

    data class NewState(
        val stateId: StateId,
    ) : Node

    data object Dead : Node

    data object Terminated : Node

    data class Edge(
        val locOccurrence: Int,
        val inst: TvmInst,
    ) {
        val instLoc: TvmPhysicalInstLocation?
            get() = (inst as? TvmRealInst)?.physicalLocation
    }

    private val edges: MutableMap<StateId, MutableList<Pair<Edge, Node>>> = hashMapOf()
    private val backEdges: MutableMap<StateId, Pair<Edge, StateId>> = hashMapOf()

    private lateinit var curInst: TvmInst

    private val instCounter: MutableMap<Pair<StateId, TvmPhysicalInstLocation?>, Int> = hashMapOf()

    override fun onStatePeeked(state: TvmState) {
        curInst = state.lastStmt
        val loc = (curInst as? TvmRealInst)?.physicalLocation
        val oldValue = instCounter.getOrDefault(state.id to loc, 0)
        instCounter[state.id to loc] = oldValue + 1
    }

    private fun getEdge(state: TvmState): Edge {
        val loc = (curInst as? TvmRealInst)?.physicalLocation
        val count =
            instCounter[state.id to loc]
                ?: error("unexpected null")
        return Edge(count, curInst)
    }

    override fun onState(
        parent: TvmState,
        forks: Sequence<TvmState>,
    ) {
        val edge = getEdge(parent)
        val edgeList =
            edges.getOrPut(parent.id) {
                mutableListOf()
            }
        forks.forEach { newState ->
            val node = NewState(newState.id)
            edgeList.add(edge to node)
            backEdges[newState.id] = edge to parent.id
        }
    }

    override fun onStateTerminated(
        state: TvmState,
        stateReachable: Boolean,
    ) {
        val to = if (stateReachable) Terminated else Dead
        val edge = getEdge(state)
        val edgeList =
            edges.getOrPut(state.id) {
                mutableListOf()
            }
        edgeList.add(edge to to)
    }

    fun getPath(stateId: StateId): List<Edge> {
        val result = mutableListOf<Edge>()

        var cur = stateId
        var edge = backEdges[cur]
        while (edge != null) {
            result.add(edge.first)
            cur = edge.second
            edge = backEdges[cur]
        }

        result.reverse()
        return result
    }

    private fun printLoc(edge: Edge): String {
        val loc = edge.instLoc
        return "(${loc?.cellHashHex},${loc?.offset})#${edge.locOccurrence}"
    }

    // this function is useful when debugging
    @Suppress("unused")
    fun getPathPretty(stateId: StateId): String {
        val path = getPath(stateId)
        return path.joinToString(separator = "\n") {
            printLoc(it)
        }
    }

    // this function is useful when debugging
    @Suppress("unused")
    fun getForkedStates(
        from: StateId,
        loc: TvmPhysicalInstLocation?,
        occurrence: Int,
    ): List<Node> {
        val edges =
            edges[from]
                ?: return emptyList()
        return edges
            .filter {
                it.first.instLoc == loc && it.first.locOccurrence == occurrence
            }.map {
                it.second
            }
    }

    private fun print(): String =
        buildString {
            edges.entries.sortedBy { it.key }.forEach { (from, toEdges) ->
                appendLine("$from:")
                toEdges.forEach { (edge, to) ->
                    append("  -> $to by ")
                    append(printLoc(edge))
                    appendLine()
                }
                appendLine()
            }
        }

    override fun onMachineStopped() {
        logger.debug {
            print()
        }
    }
}
