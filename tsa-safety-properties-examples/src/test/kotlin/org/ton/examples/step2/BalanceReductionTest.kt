package org.ton.examples.step2

import FIFT_STDLIB_RESOURCE
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.machine.getResourcePath
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Test
import kotlin.test.assertTrue

class BalanceReductionTest {
    private val storageContractResourcePath = "/examples/step2/storage.fc"
    private val checkerResourcePath = "/examples/step2/balance_reduction_checker.fc"

    @Test
    fun testBalanceReduction() {
        val storageContractPath = getResourcePath<BalanceReductionTest>(storageContractResourcePath)
        val storageContractCode = getFuncContract(storageContractPath, FIFT_STDLIB_RESOURCE)

        val checkerPath = getResourcePath<BalanceReductionTest>(checkerResourcePath)
        val checkerCode = getFuncContract(checkerPath, FIFT_STDLIB_RESOURCE, isTSAChecker = true)

        val options =
            TvmOptions(
                intercontractOptions = IntercontractOptions(communicationScheme = null),
                turnOnTLBParsingChecks = false,
            )

        val contracts = listOf(checkerCode, storageContractCode)
        val result =
            analyzeInterContract(
                contracts,
                startContractId = 0, // Checker contract is the first to analyze
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )
        val failures = result.tests.filter { it.result is TvmTestFailure }

        val balanceReductionExecutions = failures.filter { (it.result as TvmTestFailure).exitCode == 256 }

        assertTrue(balanceReductionExecutions.isNotEmpty())
    }
}
