package org.usvm.machine

import org.ton.bytecode.MethodId
import org.ton.bytecode.TvmArtificialInst
import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmInstMethodLocation
import org.ton.bytecode.TvmMainMethodLocation
import org.ton.bytecode.TvmMethod
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmState
import org.usvm.statistics.UMachineObserver
import java.util.Collections.newSetFromMap
import java.util.IdentityHashMap
import org.ton.bytecode.TvmContDictCalldictInst
import org.ton.bytecode.TvmContDictCalldictLongInst
import org.ton.bytecode.TvmContDictJmpdictInst
import org.ton.bytecode.TvmContDictPreparedictInst
import org.ton.bytecode.flattenStatements

// Tracks coverage of all visited statements for all visited methods from all states.
// Note that one instance should be used only one per method.
class TvmCoverageStatistics(
    private val observedContractId: ContractId,
    private val contractCode: TsaContractCode
) : UMachineObserver<TvmState> {
    private val coveredStatements: MutableSet<TvmInst> = newSetFromMap(IdentityHashMap())
    private val reachableMethods: MutableSet<MethodId> = hashSetOf()
    private val traversedMethodStatements: MutableMap<MethodId, List<TvmInst>> = hashMapOf()
    private val coveredStatementsFromMain: MutableSet<TvmInst> = hashSetOf()

    fun getMethodCoveragePercents(methodId: MethodId): Float? {
        val method = contractCode.methods[methodId]
            ?: return null

        val methodStatements = getMethodStatements(method)
        val coveredMethodStatements = methodStatements.count { it in coveredStatements }

        return computeCoveragePercents(coveredMethodStatements, methodStatements.size)
    }

    fun getMainMethodCoveragePercents(): Float {
        val allStatements = contractCode.mainMethod.instList.flattenStatements()
        return computeCoveragePercents(coveredStatementsFromMain.size, allStatements.size)
    }

    fun getTransitiveCoveragePercents(): Float? {
        val allStatements = reachableMethods.flatMap(::getMethodStatements)

        if (allStatements.isEmpty()) {
            return null
        }

        return computeCoveragePercents(coveredStatements.size, allStatements.size)
    }

    private fun computeCoveragePercents(covered: Int, all: Int): Float {
        if (all == 0) {
            return 100f
        }

        return covered.toFloat() / (all.toFloat()) * 100f
    }

    private fun getMethodStatements(methodId: MethodId): List<TvmInst> {
        val method = contractCode.methods[methodId]
            ?: error("Unknown method with id $methodId")

        return getMethodStatements(method)
    }

    private fun getMethodStatements(method: TvmMethod): List<TvmInst> =
        traversedMethodStatements.getOrPut(method.id) {
            method.instList.flattenStatements()
        }

    private fun addReachableMethod(methodId: MethodId) {
        if (methodId in reachableMethods) {
            return
        }

        val methodsToVisit = mutableListOf(methodId)

        while (methodsToVisit.isNotEmpty()) {
            val curId = methodsToVisit.removeLast()

            if (!reachableMethods.add(curId)) {
                continue
            }

            getMethodStatements(curId).forEach { stmt ->
                when (stmt) {
                    is TvmContDictCalldictInst -> methodsToVisit.add(stmt.n.toMethodId())
                    is TvmContDictCalldictLongInst -> methodsToVisit.add(stmt.n.toMethodId())
                    is TvmContDictPreparedictInst -> methodsToVisit.add(stmt.n.toMethodId())
                    is TvmContDictJmpdictInst -> methodsToVisit.add(stmt.n.toMethodId())
                    else -> {}
                }
            }
        }
    }

    override fun onStatePeeked(state: TvmState) {
        val stmt = state.currentStatement
        if (stmt is TvmArtificialInst || state.currentContract != observedContractId) {
            return
        }

        val rootLocation = stmt.getRootLocation()
        // instructions from main are counted separately
        if (rootLocation is TvmMainMethodLocation) {
            coveredStatementsFromMain.add(stmt)
            return
        }

        coveredStatements.add(stmt)

        val location = stmt.location
        if (location is TvmInstMethodLocation) {
            addReachableMethod(location.methodId)
        }
    }
}