package org.usvm.machine

import org.ton.TvmContractHandlers
import org.usvm.machine.TvmMachine.Companion.DEFAULT_LOOP_ITERATIONS_LIMIT
import org.usvm.machine.TvmMachine.Companion.DEFAULT_MAX_CELL_DEPTH_FOR_DEFAULT_CELLS_CONSISTENT_WITH_TLB
import org.usvm.machine.TvmMachine.Companion.DEFAULT_MAX_RECURSION_DEPTH
import org.usvm.machine.TvmMachine.Companion.DEFAULT_MAX_TLB_DEPTH
import org.usvm.machine.state.ContractId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class TvmOptions(
    val quietMode: Boolean = false,
    val enableExternalAddress: Boolean = false,
    val useReceiverInputs: Boolean = true,
    val enableInputValues: Boolean = true,
    val turnOnTLBParsingChecks: Boolean = true,
    val performAdditionalChecksWhileResolving: Boolean = false,
    val tlbOptions: TlbOptions = TlbOptions(),
    val maxRecursionDepth: Int = DEFAULT_MAX_RECURSION_DEPTH,
    val timeout: Duration = Duration.INFINITE,
    val solverTimeout: Duration = 1.seconds,
    val excludeExecutionsWithFailures: Boolean = false,
    val loopIterationLimit: Int = DEFAULT_LOOP_ITERATIONS_LIMIT,
    val intercontractOptions: IntercontractOptions = IntercontractOptions(),
    val useMainMethodForInitialMethodJump: Boolean = true,
    val analyzeBouncedMessaged: Boolean = false,
    val enableOutMessageAnalysis: Boolean = false,
    val alwaysUseSymbolicLengthInStoreCoins: Boolean = false,
    val turnOffIntBlasting: Boolean = false,
) {
    init {
        check(enableOutMessageAnalysis || !intercontractOptions.isIntercontractEnabled) {
            "Cannot perform inter-contract analysis without enabling out messages analysis"
        }
    }
}

data class TlbOptions(
    val performTlbChecksOnAllocatedCells: Boolean = false,
    val maxTlbDepth: Int = DEFAULT_MAX_TLB_DEPTH,
    val maxCellDepthForDefaultCellsConsistentWithTlb: Int = DEFAULT_MAX_CELL_DEPTH_FOR_DEFAULT_CELLS_CONSISTENT_WITH_TLB,
) {
    init {
        check(maxTlbDepth >= 0) {
            "maxTlbDepth must be non-negative, but it is $maxTlbDepth"
        }
        check(maxCellDepthForDefaultCellsConsistentWithTlb >= 0) {
            "maxCellDepthForDefaultCellsConsistentWithTlb must be non-negative, " +
                    "but it is $maxCellDepthForDefaultCellsConsistentWithTlb"
        }
    }
}

data class IntercontractOptions(
    val communicationScheme: Map<ContractId, TvmContractHandlers>? = null,
) {
    val isIntercontractEnabled: Boolean
        get() = communicationScheme != null
}
