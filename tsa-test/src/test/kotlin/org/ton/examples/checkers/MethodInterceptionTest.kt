package org.ton.examples.checkers

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.ton.test.utils.assertInvariantsHold
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.doesNotEndWithExitCode
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.hasExitCode
import org.usvm.machine.analyzeInterContract
import kotlin.test.Test

@Tag("intercontract")
class MethodInterceptionTest {
    private object MethodInterceptionTestData {
        const val CHECKER = "/checkers/method-interception/checker.fc"
        const val CHECKER_MISSING_SUBSTITUTION =
            "/checkers/method-interception/checker_missing_substitution.fc"
        const val CONTRACT = "/checkers/method-interception/contract.fc"
        const val GLOBAL_DATA_CHECKER = "/checkers/method-interception/global-data-checker.fc"
        const val GLOBAL_DATA_CONTRACT = "/checkers/method-interception/global-data-contract.fc"
    }

    @Test
    fun `method interception substitutes methods with various arities`() {
        val checkerContract = extractCheckerContractFromResource(MethodInterceptionTestData.CHECKER)
        val contract = extractFuncContractFromResource(MethodInterceptionTestData.CONTRACT)
        val tests = analyzeInterContract(listOf(checkerContract, contract))

        tests.assertPropertiesFound(hasExitCode(1000))
        tests.assertInvariantsHold(
            doesNotEndWithExitCode(101),
            doesNotEndWithExitCode(102),
            doesNotEndWithExitCode(103),
            doesNotEndWithExitCode(104),
            doesNotEndWithExitCode(105),
            doesNotEndWithExitCode(299),
        )
    }

    @Test
    fun `method interception does not corrupt global data of checker or called contract`() {
        val checkerContract = extractCheckerContractFromResource(MethodInterceptionTestData.GLOBAL_DATA_CHECKER)
        val contract = extractFuncContractFromResource(MethodInterceptionTestData.GLOBAL_DATA_CONTRACT)
        val tests = analyzeInterContract(listOf(checkerContract, contract))

        tests.assertPropertiesFound(hasExitCode(1000))
        tests.assertInvariantsHold(
            doesNotEndWithExitCode(101),
            doesNotEndWithExitCode(102),
            doesNotEndWithExitCode(201),
            doesNotEndWithExitCode(202),
            doesNotEndWithExitCode(301),
        )
    }

    @Test
    fun `method interception errors out on missing substitution method`() {
        val checkerContract =
            extractCheckerContractFromResource(MethodInterceptionTestData.CHECKER_MISSING_SUBSTITUTION)
        val contract = extractFuncContractFromResource(MethodInterceptionTestData.CONTRACT)

        assertThrows<Throwable> {
            analyzeInterContract(listOf(checkerContract, contract))
        }
    }
}
