package org.ton.examples.checkers

import org.ton.cell.buildCell
import org.ton.test.utils.extractBocContractFromResource
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.state.TvmSymbolicC5
import org.usvm.test.resolver.TvmExecutionWithSoftFailure
import java.math.BigInteger
import kotlin.test.Test

class SymbolicC5 {
    val checkerExternal = "/checkers/symbolic-c5/checker_external.fc"
    val vulnerableExternal = "/checkers/symbolic-c5/vulnerable.compiled.json"

    @Test
    fun testVulnerabilityInRecvExternal() {
        val checker = extractCheckerContractFromResource(checkerExternal)
        val contract = extractBocContractFromResource(vulnerableExternal)

        val tests =
            analyzeInterContract(
                listOf(checker, contract),
                startContractId = 0,
                methodId = BigInteger.ZERO,
                options = TvmOptions(enableOutMessageAnalysis = true),
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(),
                        TvmConcreteContractData(
                            contractC4 =
                                buildCell {
                                    storeUInt(0, 256)
                                },
                        ),
                    ),
            )

        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmExecutionWithSoftFailure)?.failure?.exit == TvmSymbolicC5 },
        )
    }
}
