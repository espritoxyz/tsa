package org.usvm.machine

import org.ton.bytecode.TvmArtificialInst
import org.ton.bytecode.TvmContractCode
import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmInstMethodLocation
import org.ton.bytecode.TvmMethod
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmState
import org.usvm.statistics.UMachineObserver
import java.util.Collections.newSetFromMap
import java.util.IdentityHashMap
import org.ton.bytecode.flattenStatements

// Tracks coverage of all visited statements for all visited methods from all states.
// Note that one instance should be used only one per method.
class TvmCoverageStatistics(
    private val observedContractId: ContractId,
    private val contractCode: TvmContractCode
) : UMachineObserver<TvmState> {
    private val coveredStatements: MutableSet<TvmInst> = newSetFromMap(IdentityHashMap())
    private val visitedMethods: MutableSet<MethodId> = hashSetOf()
    private val traversedMethodStatements: MutableMap<MethodId, List<TvmInst>> = hashMapOf()

    fun getMethodCoveragePercents(method: TvmMethod): Float {
        val methodStatements = getMethodStatements(method)
        val coveredMethodStatements = methodStatements.count { it in coveredStatements }

        return computeCoveragePercents(coveredMethodStatements, methodStatements.size)
    }

    fun getTransitiveCoveragePercents(): Float {
        val allStatements = visitedMethods.flatMap { methodId ->
            val method = contractCode.methods[methodId]
                ?: error("Unknown method with id $methodId")

            getMethodStatements(method)
        }

        return computeCoveragePercents(coveredStatements.size, allStatements.size)
    }

    private fun computeCoveragePercents(covered: Int, all: Int): Float {
        if (all == 0) {
            return 100f
        }

        return covered.toFloat() / (all.toFloat()) * 100f
    }

    private fun getMethodStatements(method: TvmMethod): List<TvmInst> {
        val methodId = method.id
        val alreadyTraversedStatements = traversedMethodStatements[methodId]
        if (alreadyTraversedStatements != null) {
            return alreadyTraversedStatements
        }

        val methodStatements = method.instList.flattenStatements()

        traversedMethodStatements[methodId] = methodStatements
        return methodStatements
    }

    override fun onStatePeeked(state: TvmState) {
        val stmt = state.currentStatement
        if (stmt is TvmArtificialInst || state.currentContract != observedContractId) {
            return
        }

        coveredStatements.add(stmt)

        val location = stmt.location
        if (location is TvmInstMethodLocation) {
            visitedMethods.add(location.methodId)
        }
    }
}