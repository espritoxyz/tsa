package org.usvm.machine

import org.ton.bytecode.MethodId
import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.TvmArtificialInst
import org.ton.bytecode.TvmContDictCalldictInst
import org.ton.bytecode.TvmContDictCalldictLongInst
import org.ton.bytecode.TvmContDictJmpdictInst
import org.ton.bytecode.TvmContDictPreparedictInst
import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmInstMethodLocation
import org.ton.bytecode.TvmMainMethod
import org.ton.bytecode.TvmMainMethodLocation
import org.ton.bytecode.TvmMethod
import org.ton.bytecode.flattenStatements
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmState
import org.usvm.statistics.UMachineObserver
import java.util.Collections.newSetFromMap
import java.util.IdentityHashMap

// Tracks coverage of all visited statements for all visited methods from all states.
// Note that one instance should be used only one per method.
class TvmCoverageStatistics(
    private val observedContractId: ContractId,
    private val mainMethod: TvmMainMethod,
) : UMachineObserver<TvmState> {
    private val coveredStatements: MutableSet<TvmInst> = newSetFromMap(IdentityHashMap())
    private val reachableMethods: MutableSet<TvmMethod> = hashSetOf()
    private val traversedMethodStatements: MutableMap<MethodId, List<TvmInst>> = hashMapOf()
    private val coveredStatementsFromMain: MutableSet<TvmInst> = hashSetOf()
    private var wasC3Changed: Boolean = false

    fun getMethodCoveragePercents(methodId: MethodId): Float? {
        val method =
            reachableMethods.firstOrNull { it.id == methodId }
                ?: return null

        val methodStatements = getMethodStatements(method)
        val coveredMethodStatements = methodStatements.count { it in coveredStatements }

        return computeCoveragePercents(coveredMethodStatements, methodStatements.size)
    }

    fun getMainMethodCoveragePercents(): Float? {
        // cannot calculate main method coverage in this case
        if (wasC3Changed) {
            return null
        }
        val allStatements = mainMethod.instList.flattenStatements()
        return computeCoveragePercents(coveredStatementsFromMain.size, allStatements.size)
    }

    fun getTransitiveCoveragePercents(): Float? {
        val allStatements = reachableMethods.flatMap(::getMethodStatements)

        if (allStatements.isEmpty()) {
            return null
        }

        return computeCoveragePercents(coveredStatements.size, allStatements.size)
    }

    private fun computeCoveragePercents(
        covered: Int,
        all: Int,
    ): Float {
        if (all == 0) {
            return 100f
        }

        return covered.toFloat() / (all.toFloat()) * 100f
    }

    private fun getMethodStatements(method: TvmMethod): List<TvmInst> =
        traversedMethodStatements.getOrPut(method.id) {
            method.instList.flattenStatements()
        }

    private fun addReachableMethod(
        methodId: MethodId,
        currentCode: TsaContractCode,
    ) {
        if (reachableMethods.any { it.id == methodId }) {
            return
        }

        val methodsToVisit = mutableListOf(methodId)

        while (methodsToVisit.isNotEmpty()) {
            val curId = methodsToVisit.removeLast()
            val method =
                currentCode.methods[curId]
                    ?: continue

            if (!reachableMethods.add(method)) {
                continue
            }

            getMethodStatements(method).forEach { stmt ->
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

        val currentCode = state.registersOfCurrentContract.c3.code
        if (currentCode.parentCode != null) {
            wasC3Changed = true
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
            addReachableMethod(location.methodId, currentCode)
        }
    }
}
