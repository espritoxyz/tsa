package org.ton.examples.checkers

import org.ton.cell.CellBuilder
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.hasExitCode
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.analyzeInterContract
import org.usvm.test.resolver.TvmSymbolicTestSuite
import kotlin.test.Test

/**
 * Very brittle reproduction of the bug where registers get shared across states after executing `checkerReturns` after
 * a fork within a handler.
 */
class ForkWithoutSolverSharedC4Test {
    private val analyzed = "/checkers/fork-shared-c4/analyzed.fc"

    private fun run(checkerPath: String): TvmSymbolicTestSuite {
        val checkerContract = extractCheckerContractFromResource(checkerPath)
        val analyzedContract = extractFuncContractFromResource(analyzed)

        val analyzedC4 =
            CellBuilder()
                .storeUInt(4, 3) // addr_std$10 + anycast 0
                .storeUInt(0, 8) // workchain
                .storeUInt(13, 256) // account id
                .storeUInt(0, 64) // counter
                .storeUInt(0, 32) // seqno
                .endCell()

        val tests =
            analyzeInterContract(
                contracts = listOf(checkerContract, analyzedContract),
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(),
                        TvmConcreteContractData(contractC4 = analyzedC4),
                    ),
            )
        return tests
    }

    private val noForkChecker = "/checkers/fork-shared-c4/checker-without-analysis-fork.fc"
    private val forkChecker = "/checkers/fork-shared-c4/checker-with-analysis-fork.fc"

    @Test
    fun `fork inside checker handler loses an execution because of shared c4 register`() {
        val noForkResults = run(noForkChecker)
        noForkResults.assertPropertiesFound(hasExitCode(1001))
        val forkResults = run(forkChecker)
        forkResults.assertPropertiesFound(hasExitCode(1001))
    }
}
