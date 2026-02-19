package org.ton.examples.checkers

import org.junit.jupiter.api.Tag
import org.ton.bytecode.TsaContractCode
import org.ton.test.utils.assertInvariantsHold
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.exitCode
import org.ton.test.utils.extractBocContractFromResource
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.state.InsufficientFunds
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmSymbolicTestSuite
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Test

@Tag("intercontract")
class RandomSenderTest {
    private val tonDrainChecker = "/checkers/random-sender/drain_checker.fc"
    private val vulnerableContract = "/checkers/random-sender/simple_vulnerable_contract.fc"
    private val vulnerableTutorialContract = "/checkers/random-sender/vulnerable-tolk.json"
    private val vulnerableContractWithHash = "/checkers/random-sender/vulnerable_contract_with_hash.fc"
    private val vulnerableContractWithHashInC4 = "/checkers/random-sender/vulnerable_contract_with_hash_in_c4.fc"
    private val vulnerableContractWithHashOfSender =
        "/checkers/random-sender/vulnerable_contract_with_hash_of_sender.fc"

    private val nonVulnerableContract = "/checkers/random-sender/non_vulnerable_contract.fc"
    private val nonVulnerableContractWithHash = "/checkers/random-sender/non_vulnerable_contract_with_hash.fc"
    private val vulnerableWithOverflow = "/checkers/random-sender/simple_vulnerable_contract_with_overflow.fc"

    private fun runTestGeneral(analyzedContract: TsaContractCode): TvmSymbolicTestSuite {
        val checkerContract = extractCheckerContractFromResource(tonDrainChecker)

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

    private fun runTest(contractPath: String): TvmSymbolicTestSuite {
        val analyzedContract = extractFuncContractFromResource(contractPath)
        return runTestGeneral(analyzedContract)
    }

    private fun runTestJson(contractPath: String): TvmSymbolicTestSuite {
        val analyzedContract = extractBocContractFromResource(contractPath)
        return runTestGeneral(analyzedContract)
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
    fun testVulnerableTolk() {
        val tests = runTestJson(vulnerableTutorialContract)

        fun isInsufficientFundsFailure(test: TvmSymbolicTest): Boolean {
            val result = test.result
            return result is TvmTestFailure && result.failure.exit is InsufficientFunds
        }
        tests.assertInvariantsHold(
            { test -> !isInsufficientFundsFailure(test) },
        )
    }

    @Test
    fun testVulnerableToTonDrainWithHash() {
        val tests = runTest(vulnerableContractWithHash)

        propertiesFound(
            tests,
            listOf(
                { test -> test.exitCode() == 500 },
                { test -> test.exitCode() == 1000 },
            ),
        )
    }

    @Test
    fun testVulnerableToTonDrainWithHashInC4() {
        val tests = runTest(vulnerableContractWithHashInC4)

        propertiesFound(
            tests,
            listOf(
                { test -> test.exitCode() == 500 },
                { test -> test.exitCode() == 1000 },
            ),
        )
    }

    @Test
    fun testVulnerableToTonDrainWithHashOfSender() {
        val tests = runTest(vulnerableContractWithHashOfSender)

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

    @Test
    fun testNonVulnerableToTonDrainWithHash() {
        val tests = runTest(nonVulnerableContractWithHash)

        propertiesFound(
            tests,
            listOf { test -> test.exitCode() == 500 },
        )

        checkInvariants(
            tests,
            listOf { test -> test.exitCode() != 1000 },
        )
    }

    @Test
    fun testVulnerableWithCellOverflow() {
        val tests = runTest(vulnerableWithOverflow)

        propertiesFound(
            tests,
            listOf(
                { test -> test.exitCode() == 1000 },
            ),
        )
    }
}
