package org.ton.examples.checkers

import org.ton.test.utils.checkInvariants
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TvmContext
import org.usvm.machine.analyzeInterContract
import org.usvm.test.resolver.TvmMethodFailure
import kotlin.test.Test
import kotlin.test.Ignore
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractCommunicationSchemeFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.usvm.machine.TvmOptions
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.state.ContractId
import org.ton.TvmContractHandlers

class ForwardFeesTest {
    private val checker = "/fees/forward/checker.fc"
    private val sender = "/fees/forward/sender.fc"
    private val receiver = "/fees/forward/receiver.fc"
    private val scheme = "/fees/forward/inter-contract.json"

    @Ignore("Proper calculation is not supported yet")
    @Test
    fun forwardFeesTest() {
        val checkerContract = extractCheckerContractFromResource(checker)
        val analyzedSender = extractFuncContractFromResource(sender)
        val analyzedReceiver = extractFuncContractFromResource(receiver)
        val communicationScheme = extractCommunicationSchemeFromResource(scheme)

        val options =
            TvmOptions(
                intercontractOptions = IntercontractOptions(communicationScheme = communicationScheme),
                turnOnTLBParsingChecks = false,
                enableOutMessageAnalysis = true,
                stopOnFirstError = false,
            )

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedSender, analyzedReceiver),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        propertiesFound(
            tests,
            listOf { test ->
                val checkerExitCode = test.eventsList.single { it.id == 0 }.methodResult
                val checkerExitCodeGood = (checkerExitCode as? TvmMethodFailure)?.exitCode == 10000
                checkerExitCodeGood
            },
        )

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode != 228 },
        )
    }
}
