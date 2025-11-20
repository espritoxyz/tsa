package org.ton.examples.bounce

import org.ton.TvmContractHandlers
import org.ton.bitstring.BitString
import org.ton.cell.Cell
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.exitCode
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractCommunicationSchemeFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getResourcePath
import org.usvm.machine.state.ContractId
import org.usvm.test.resolver.TvmExecutionWithSoftFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmTestFailure
import org.usvm.test.resolver.TvmTestInput
import kotlin.test.Test
import kotlin.test.assertTrue

class BouncedMessageTest {
    private val bounceAssertsPath = "/bounce/bounce_asserts.fc"
    private val bounceCheckerPath = "/bounce/bounce.fc"
    private val senderBouncePath = "/bounce/send_bounce_true.fc"
    private val recipientBouncePath = "/bounce/receive_bounce_msg.fc"
    private val recipientBounceSoftFailurePath = "/bounce/receive_bounce_msg_with_soft_failure.fc"
    private val bounceTestScheme = "/bounce/bounce-test-scheme.json"
    private val bounceFormatContract = "/bounce/bounce_format_send.fc"
    private val bounceFormatChecker = "/bounce/bounce_format.fc"
    private val bounceFormatScheme = "/bounce/bounce_format_scheme.json"

    @Test
    fun testBounceInput() {
        val resourcePath = getResourcePath<BouncedMessageTest>(bounceAssertsPath)
        val options = TvmOptions(analyzeBouncedMessaged = true)
        val results = funcCompileAndAnalyzeAllMethods(resourcePath, tvmOptions = options)
        val tests = results.testSuites.single()

        checkInvariants(
            tests,
            listOf { test ->
                test.result is TvmSuccessfulExecution
            },
        )

        propertiesFound(
            tests,
            listOf { test ->
                (test.input as? TvmTestInput.RecvInternalInput)?.bounced == true
            },
        )
    }

    @Test
    fun bounceTest() {
        val checkerContract = extractCheckerContractFromResource(bounceCheckerPath)
        val analyzedSender = extractFuncContractFromResource(senderBouncePath)
        val analyzedRecipient = extractFuncContractFromResource(recipientBouncePath)
        val communicationScheme = extractCommunicationSchemeFromResource(bounceTestScheme)
        val options = createIntercontractOptions(communicationScheme)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedSender, analyzedRecipient),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(),
                        TvmConcreteContractData(contractC4 = Cell(BitString.of("0"))),
                        TvmConcreteContractData(),
                    ),
            )

        propertiesFound(
            tests,
            listOf { test ->
                val checkerExitCode = test.exitCode()
                val checkerExitCodeGood = checkerExitCode == 258
                val thereWereThreeFailedMethod =
                    test.eventsList.count { it.computePhaseResult is TvmTestFailure } == 2
                checkerExitCodeGood && thereWereThreeFailedMethod
            },
        )

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode != 257 },
        )
    }

    @Test
    fun `message does not bounce on soft failure`() {
        val checkerContract = extractCheckerContractFromResource(bounceCheckerPath)
        val analyzedSender = extractFuncContractFromResource(senderBouncePath)
        val analyzedRecipient = extractFuncContractFromResource(recipientBounceSoftFailurePath)
        val communicationScheme = extractCommunicationSchemeFromResource(bounceTestScheme)
        val options = createIntercontractOptions(communicationScheme)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedSender, analyzedRecipient),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(),
                        TvmConcreteContractData(contractC4 = Cell(BitString.of("0"))),
                        TvmConcreteContractData(),
                    ),
            )

        assertTrue(tests.tests.isNotEmpty())
        checkInvariants(
            tests,
            listOf { test ->
                val receivedBounceOnSoftFailureExitCode = 258
                val messageDidNotBounce =
                    (test.result as? TvmTestFailure)?.exitCode != receivedBounceOnSoftFailureExitCode
                val softFailureOccurred = test.result is TvmExecutionWithSoftFailure
                messageDidNotBounce && softFailureOccurred
            },
        )
    }

    @Test
    fun bounceFormatTest() {
        val checkerContract = extractCheckerContractFromResource(bounceFormatChecker)
        val analyzedSender = extractFuncContractFromResource(bounceFormatContract)
        val analyzedRecipient = extractFuncContractFromResource(recipientBouncePath)
        val communicationScheme = extractCommunicationSchemeFromResource(bounceFormatScheme)
        val options = createIntercontractOptions(communicationScheme)
        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedSender, analyzedRecipient),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(),
                        TvmConcreteContractData(contractC4 = Cell(BitString.of("0"))),
                        TvmConcreteContractData(),
                    ),
            )

        propertiesFound(
            tests,
            // the target contract should change its persistent data
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode == 255 },
        )
        checkInvariants(
            tests,
            listOf { test ->
                test.eventsList.mapNotNull { it.computePhaseResult as? TvmTestFailure }.all { failedResult ->
                    val formatViolationExitCodes = 257..265
                    failedResult.exitCode !in formatViolationExitCodes
                }
            },
        )
    }

    private fun createIntercontractOptions(communicationScheme: Map<ContractId, TvmContractHandlers>): TvmOptions =
        TvmOptions(
            intercontractOptions =
                IntercontractOptions(
                    communicationScheme = communicationScheme,
                ),
            turnOnTLBParsingChecks = false,
            enableOutMessageAnalysis = true,
            stopOnFirstError = false,
        )
}
