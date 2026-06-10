package org.ton.examples.checkers

import org.ton.examples.intercontract.implies
import org.ton.test.utils.assertInvariantsHold
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.toMethodId
import org.usvm.test.resolver.exitCode
import kotlin.test.Test

class AuthorizationWhitelistTest {
    @Test
    fun `checker playground test`() {
        val checker = extractCheckerContractFromResource("/checkers/whitelist/checker.fc")
        val contract = extractFuncContractFromResource("/checkers/whitelist/whitelisted-contract.fc")
        val tests = analyzeInterContract(listOf(checker, contract), 0, 0.toMethodId())
        tests.assertPropertiesFound(
            { test -> test.result.exitCode() == 1000 },
        )

        tests.assertInvariantsHold(
            { test -> (test.result.exitCode() == 1000) implies { test.resolvedEnumeratedValues[0]!!.size == 2 } },
        )
    }
}
