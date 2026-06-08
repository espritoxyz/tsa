package org.ton.examples.checkers

import org.ton.test.utils.assertInvariantsHold
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.doesNotEndWithExitCode
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.hasExitCode
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.toMethodId
import kotlin.test.Test

class SetAddressTest {
    val checkerResourcePath = "/checkers/set-address/checker.fc"
    val contractResourcePath = "/checkers/set-address/contract.fc"

    @Test
    fun `set address test`() {
        val checker = extractCheckerContractFromResource(checkerResourcePath)
        val contract = extractFuncContractFromResource(contractResourcePath)
        val tests =
            analyzeInterContract(
                contracts = listOf(checker, contract),
                startContractId = 0,
                methodId = 0.toMethodId(),
            )
        tests.assertPropertiesFound(hasExitCode(1000))
        tests.assertInvariantsHold(
            doesNotEndWithExitCode(300),
            doesNotEndWithExitCode(400),
        )
    }
}
