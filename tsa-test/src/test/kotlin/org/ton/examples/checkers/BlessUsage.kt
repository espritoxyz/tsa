package org.ton.examples.checkers

import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.state.TvmUsageOfBless
import org.usvm.test.resolver.TvmExecutionWithSoftFailure
import java.math.BigInteger
import kotlin.test.Test

class BlessUsage {
    val checkerInternal = "/checkers/bless/checker_internal.fc"
    val vulnerableInternal = "/checkers/bless/vulnerable_internal.fc"

    @Test
    fun testVulnerabilityInRecvInternal() {
        val checker = extractCheckerContractFromResource(checkerInternal)
        val contract = extractFuncContractFromResource(vulnerableInternal)

        val tests =
            analyzeInterContract(
                listOf(checker, contract),
                startContractId = 0,
                methodId = BigInteger.ZERO,
                options = TvmOptions(enableOutMessageAnalysis = true),
            )

        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmExecutionWithSoftFailure)?.failure?.exit == TvmUsageOfBless },
        )
    }
}
