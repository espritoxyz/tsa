package org.ton.examples.checkers

import org.ton.test.utils.assertInvariantsHold
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.exitCode
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.test.resolver.TvmSymbolicTestSuite
import kotlin.test.Test

class OwnerHijackTest {
    private val ownerHijackChecker = "/checkers/owner-hijack/owner-hijack.fc"
    private val vulnerableContract = "/checkers/owner-hijack/simple-vulnerable-contract.fc"
    private val invulnerableContractTrivial = "/checkers/owner-hijack/invulnerable-contract-trivial.fc"
    private val invulnerableContractWithOwner = "/checkers/owner-hijack/invulnerable-contract-with-owner.fc"

    private fun runTest(contractPath: String): TvmSymbolicTestSuite {
        val checkerContract = extractCheckerContractFromResource(ownerHijackChecker)
        val analyzedContract = extractFuncContractFromResource(contractPath)

        val options =
            TvmOptions(
                stopOnFirstError = false,
                enableOutMessageAnalysis = true,
            )

        return analyzeInterContract(
            listOf(checkerContract, analyzedContract),
            startContractId = 0,
            methodId = TvmContext.RECEIVE_INTERNAL_ID,
            options = options,
        )
    }

    @Test
    fun testVulnerable() {
        val tests = runTest(vulnerableContract)
        tests.assertPropertiesFound(
            { test -> test.exitCode() == 500 },
        )
    }

    @Test
    fun testInVulnerableTrivial() {
        val tests = runTest(invulnerableContractTrivial)
        tests.assertInvariantsHold(
            { test -> test.exitCode() != 500 },
        )
    }

    @Test
    fun testInVulnerableWithOwner() {
        val tests = runTest(invulnerableContractWithOwner)
        tests.assertInvariantsHold(
            { test -> test.exitCode() != 500 },
        )
    }
}
