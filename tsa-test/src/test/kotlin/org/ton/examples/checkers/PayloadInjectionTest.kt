package org.ton.examples.checkers

import org.junit.jupiter.api.Tag
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractCommunicationSchemeFromResource
import org.ton.test.utils.extractResource
import org.ton.test.utils.hasExitCode
import org.ton.test.utils.propertiesFound
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TactSourcesDescription
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getTactContract
import kotlin.test.Test

private const val VULNERABILITY_FOUND = 1000

@Tag("intercontract")
class PayloadInjectionTest {
    private val checkerPath = "/checkers/payload-injection/payload_injection_checker.fc"
    private val tactConfig = "/tact/simplified-dex/tact.config.json"
    private val schemePath = "/checkers/payload-injection/communication-scheme.json"

    @Test
    fun `TonVault payload injection allows arbitrary opcode`() {
        val checkerContract = extractCheckerContractFromResource(checkerPath)
        val pathTactConfig = extractResource(tactConfig)
        val tonVaultContract = getTactContract(TactSourcesDescription(pathTactConfig, "SimplifiedDex", "TonVault"))
        val ammPoolContract = getTactContract(TactSourcesDescription(pathTactConfig, "SimplifiedDex", "AmmPool"))
        val communicationScheme = extractCommunicationSchemeFromResource(schemePath)

        val options =
            TvmOptions(
                intercontractOptions = IntercontractOptions(communicationScheme = communicationScheme),
                turnOnTLBParsingChecks = false,
                enableOutMessageAnalysis = true,
                stopOnFirstError = false,
            )

        val tests =
            analyzeInterContract(
                listOf(checkerContract, tonVaultContract, ammPoolContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        propertiesFound(
            tests,
            listOf(hasExitCode(VULNERABILITY_FOUND)),
        )
    }
}
