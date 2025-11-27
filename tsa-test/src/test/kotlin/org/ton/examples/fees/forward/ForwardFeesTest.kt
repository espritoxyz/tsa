package org.ton.examples.fees.forward

import org.ton.cell.CellBuilder
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractCommunicationSchemeFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmConcreteContractData
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
    fun forwardFeesTest0() {
        forwardFeesBaseTest(0)
    }

    @Test
    fun forwardFeesTest1() {
        forwardFeesBaseTest(1)
    }

    @Test
    fun forwardFeesTest2() {
        forwardFeesBaseTest(2)
    }

    @Test
    fun forwardFeesTest3() {
        forwardFeesBaseTest(3)
    }

    @Test
    fun forwardFeesTest4() {
        forwardFeesBaseTest(4)
    }

    private fun forwardFeesBaseTest(flag: Int) {
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
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(contractC4 = CellBuilder.beginCell().storeInt(flag, 64).endCell()),
                        TvmConcreteContractData(),
                        TvmConcreteContractData(),
                    ),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode !in 201..3001 },
        )

        propertiesFound(
            tests,
            listOf { test ->
                (test.result as? TvmTestFailure)?.exitCode == 10000
            },
        )
    }
}
