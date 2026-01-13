package org.ton.examples.checkers

import org.ton.test.utils.FIFT_STDLIB_RESOURCE
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.exitCode
import org.ton.test.utils.extractResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.test.resolver.TvmSymbolicTestSuite
import kotlin.test.Test

class RandomSenderTest {
    private val tonDrainChecker = "/checkers/random-sender/drain_checker.fc"
    private val nonVulnerableContract = "/checkers/random-sender/non_vulnerable_contract.fc"
    private val vulnerableContract = "/checkers/random-sender/simple_vulnerable_contract.fc"

    private fun runTest(contractPath: String): TvmSymbolicTestSuite {
        val contractPath = extractResource(contractPath)
        val checkerPath = extractResource(tonDrainChecker)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true,
            )
        val analyzedContract = getFuncContract(contractPath, FIFT_STDLIB_RESOURCE)

        val options = TvmOptions(
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
    fun testVulnerableToTonDrain() {
        val tests = runTest(vulnerableContract)

        propertiesFound(
            tests,
            listOf(
                { test -> test.exitCode() == 500 },
                { test -> test.exitCode() == 1000 },
            ),
        )
    }

    @Test
    fun testNonVulnerableToTonDrain() {
        val tests = runTest(nonVulnerableContract)

        propertiesFound(
            tests,
            listOf { test -> test.exitCode() == 500 },
        )

        checkInvariants(
            tests,
            listOf { test -> test.exitCode() != 1000 },
        )
    }
}
