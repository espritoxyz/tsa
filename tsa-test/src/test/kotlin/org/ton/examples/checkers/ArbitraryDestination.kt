package org.ton.examples.checkers

import org.ton.cell.CellBuilder
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.extractBocContractFromResource
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.hasExitCode
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import java.math.BigInteger
import kotlin.test.Test

class ArbitraryDestination {
    val checkerInternal = "/checkers/arb-dest/checker-internal.fc"
    val stormtradeFalseNegative =
        "/checkers/arb-dest/0_bffadd270a738531da7b13ba8fc403826c2586173f9ede9c316fab53bc59ac86.boc"

    @Test
    fun testVulnerabilityInRecvInternal() {
        val checker = extractCheckerContractFromResource(checkerInternal)
        val contract = extractBocContractFromResource(stormtradeFalseNegative)
        val checkerData = CellBuilder().storeUInt(0x7362d09c, 32).endCell()

        val tests =
            analyzeInterContract(
                listOf(checker, contract),
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(contractC4 = checkerData),
                        TvmConcreteContractData(),
                    ),
                startContractId = 0,
                methodId = BigInteger.ZERO,
                options = TvmOptions(enableOutMessageAnalysis = true),
            )

        tests.assertPropertiesFound(hasExitCode(1000))
    }
}
