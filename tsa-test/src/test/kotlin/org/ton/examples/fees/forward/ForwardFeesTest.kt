package org.ton.examples.fees.forward

import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractCommunicationSchemeFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Test

class ForwardFeesTest {
    private val checker = "/fees/forward/checker.fc"
    private val sender = "/fees/forward/sender.fc"
    private val receiver = "/fees/forward/receiver.fc"
    private val scheme = "/fees/forward/inter-contract.json"

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
                (test.result as? TvmTestFailure)?.exitCode == 10000
            },
        )

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode !in 201..3001 },
        )
    }
}
