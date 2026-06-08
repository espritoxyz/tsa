package org.ton.examples.checkers

import org.ton.examples.intercontract.implies
import org.ton.test.utils.assertInvariantsHold
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.exitCode
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.hasExitCode
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.toMethodId
import kotlin.test.Test

const val INF = 5

class AuthCodeEnumerationTest {
    @Test
    fun `hash-based auth with fixed data`() {
        val checker = extractCheckerContractFromResource("/checkers/auth/auth-code-enumeration-checker.fc")
        val contract = extractFuncContractFromResource("/checkers/auth/contract-with-hash-check-fixed-data-auth.fc")
        val tests =
            analyzeInterContract(
                contracts = listOf(checker, contract),
                startContractId = 0,
                methodId = 0.toMethodId(),
            )
        tests.assertPropertiesFound(hasExitCode(1000))
        tests.assertInvariantsHold(
            {
                (it.exitCode() == 1000) implies {
                    val fetched = it.resolvedEnumeratedValues[1000]
                    fetched != null && fetched.size == 1
                }
            },
        )
    }

    @Test
    fun `hash-based auth with not fixed data`() {
        val checker = extractCheckerContractFromResource("/checkers/auth/auth-code-enumeration-checker.fc")
        val contract = extractFuncContractFromResource("/checkers/auth/contract-with-hash-check-non-fixed-data.fc")
        val tests =
            analyzeInterContract(
                contracts = listOf(checker, contract),
                startContractId = 0,
                methodId = 0.toMethodId(),
            )
        tests.assertPropertiesFound(hasExitCode(1000))
        tests.assertInvariantsHold(
            {
                (it.exitCode() == 1000) implies {
                    val fetched = it.resolvedEnumeratedValues[1000]
                    fetched != null && fetched.size == 1
                }
            },
        )
    }

    @Test
    fun `no auth`() {
        val checker = extractCheckerContractFromResource("/checkers/auth/auth-code-enumeration-checker.fc")
        val contract = extractFuncContractFromResource("/checkers/auth/contract-no-auth.fc")
        val tests =
            analyzeInterContract(
                contracts = listOf(checker, contract),
                startContractId = 0,
                methodId = 0.toMethodId(),
            )
        tests.assertPropertiesFound(hasExitCode(1000))
        tests.assertInvariantsHold(
            {
                (it.exitCode() == 1000) implies {
                    val fetched = it.resolvedEnumeratedValues[1000]
                    fetched != null && fetched.size > INF
                }
            },
        )
    }
}
