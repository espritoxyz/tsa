package org.ton.examples.checkers

import org.ton.TvmContractHandlers
import org.ton.bitstring.BitString
import org.ton.bytecode.TsaContractCode
import org.ton.cell.Cell
import org.ton.communicationSchemeFromJson
import org.ton.test.utils.FIFT_STDLIB_RESOURCE
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TactSourcesDescription
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.machine.getTactContract
import org.usvm.machine.state.ContractId
import org.usvm.test.resolver.TvmExecutionWithSoftFailure
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmMethodSymbolicResult
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmTestInput
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class CheckersTest {
    private val internalCallChecker = "/checkers/send_internal.fc"
    private val internalCallCheckerWithCapture = "/checkers/send_internal_with_capture.fc"
    private val externalCallCheckerWithCapture = "/checkers/send_external_with_capture.fc"
    private val balancePath = "/args/balance.fc"
    private val balanceExternalPath = "/args/balance_external.fc"
    private val bounceCheckerPath = "/checkers/bounce.fc"
    private val getC4CheckerPath = "/checkers/get_c4.fc"
    private val emptyContractPath = "/empty_contract.fc"
    private val senderBouncePath = "/args/send_bounce_true.fc"
    private val recipientBouncePath = "/args/receive_bounce_msg.fc"
    private val recipientBounceSoftFailurePath = "/args/receive_bounce_msg_with_soft_failure.fc"
    private val bounceTestScheme = "/checkers/bounce-test-scheme.json"
    private val tactConfig = "/tact/tact.config.json"
    private val intBlastOptimizationChecker = "/checkers/int_optimization.fc"
    private val bounceFormatContract = "/args/bounce_format_send.fc"
    private val bounceFormatChecker = "/checkers/bounce_format.fc"
    private val bounceFormatScheme = "/checkers/bounce_format_scheme.json"

    private object TransactionRollBackTestData {
        const val SENDER = "/checkers/transaction-rollback/sender-checker.fc"
        const val RECEIVER = "/checkers/transaction-rollback/receiver.fc"
    }

    private object OnOutMessageTestData {
        val checker = "/checkers/on-out-message-test/checker.fc"
        val sender = "/checkers/on-out-message-test/sender.fc"
        val receiver = "/checkers/on-out-message-test/receiver.fc"
        val communicationScheme = "/checkers/on-out-message-test/communication-scheme.json"
    }

    private object OnComputePhaseExitTestData {
        val checker = "/checkers/on-compute-phase-exit-test/checker.fc"
        val sender = "/checkers/on-compute-phase-exit-test/sender.fc"
        val receiverNoThrow = "/checkers/on-compute-phase-exit-test/receiver-nothrow.fc"
        val receiverThrow = "/checkers/on-compute-phase-exit-test/receiver-throw.fc"
        val communicationScheme = "/checkers/on-compute-phase-exit-test/communication-scheme.json"
    }

    private object OnComputePhaseExitStopOnFirstErrorTestData {
        val checker = "/checkers/on-compute-phase-exit-stop-on-first-error-test/checker.fc"
        val sender = "/checkers/on-compute-phase-exit-stop-on-first-error-test/sender.fc"
    }

    @Test
    fun testConsistentBalanceThroughChecker() {
        runTestConsistentBalanceThroughChecker(internalCallChecker, fetchedKeys = emptySet(), balancePath)
    }

    @Test
    fun testConsistentBalanceThroughCheckerWithCapture() {
        runTestConsistentBalanceThroughChecker(internalCallCheckerWithCapture, fetchedKeys = setOf(-1, -2), balancePath)
    }

    @Test
    fun testConsistentBalanceThroughCheckerWithCaptureExternal() {
        runTestConsistentBalanceThroughChecker(
            externalCallCheckerWithCapture,
            fetchedKeys = setOf(-1),
            balanceExternalPath,
        ) {
            if (it.additionalInputs.size != 1) {
                return@runTestConsistentBalanceThroughChecker false
            }
            if (it.result is TvmSuccessfulExecution) {
                (it.additionalInputs.values.single() as? TvmTestInput.RecvExternalInput)?.wasAccepted == true
            } else {
                (it.additionalInputs.values.single() as? TvmTestInput.RecvExternalInput)?.wasAccepted == false
            }
        }
    }

    private fun runTestConsistentBalanceThroughChecker(
        checkerPathStr: String,
        fetchedKeys: Set<Int>,
        contractPath: String,
        additionalCheck: (TvmSymbolicTest) -> Boolean = { true },
    ) {
        val path = extractResource(contractPath)
        val checkerPath = extractResource(checkerPathStr)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true,
            )
        val analyzedContract = getFuncContract(path, FIFT_STDLIB_RESOURCE)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
            )

        propertiesFound(
            tests,
            listOf(
                { test -> test.result is TvmSuccessfulExecution },
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1001 },
            ),
        )

        checkInvariants(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 1000 },
                additionalCheck,
            ),
        )

        val successfulTests = tests.filter { it.result is TvmSuccessfulExecution }
        checkInvariants(
            successfulTests,
            listOf { test -> fetchedKeys.all { it in test.fetchedValues } },
        )
    }

    @Test
    fun testGetC4() {
        val path = extractResource(emptyContractPath)
        val checkerPath = extractResource(getC4CheckerPath)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true,
            )
        val analyzedContract = getFuncContract(path, FIFT_STDLIB_RESOURCE)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(),
                        TvmConcreteContractData(contractC4 = Cell(BitString.of("00000100"))),
                    ),
            )

        assertTrue { tests.isNotEmpty() }

        checkInvariants(
            tests,
            listOf { test -> test.result is TvmSuccessfulExecution },
        )
    }

    @Test
    fun transactionRollBackTest() {
        val senderContract = extractCheckerContractFromResource(TransactionRollBackTestData.SENDER)
        val receiverContract = extractFuncContractFromResource(TransactionRollBackTestData.RECEIVER)
        val options = TvmOptions(stopOnFirstError = false)
        val tests =
            analyzeInterContract(
                listOf(senderContract, receiverContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(),
                        TvmConcreteContractData(contractC4 = Cell(BitString.of("0"))),
                    ),
            )

        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 258 },
        )

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode != 257 },
        )
    }

    @Test
    fun `on_out_message gets called`() {
        val checkerContract = extractCheckerContractFromResource(OnOutMessageTestData.checker)
        val senderContract = extractFuncContractFromResource(OnOutMessageTestData.sender)
        val receiverContract = extractFuncContractFromResource(OnOutMessageTestData.receiver)
        val communicationScheme = extractCommunicationSchemeFromResource(OnOutMessageTestData.communicationScheme)
        val options = createIntercontractOptions(communicationScheme)
        val tests =
            analyzeInterContract(
                listOf(checkerContract, senderContract, receiverContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        checkInvariants(
            tests,
            listOf { test -> test.eventsList.all { it.methodResult.exitCode() !in listOf(300, 301) } },
        )
        propertiesFound(
            tests,
            listOf { test -> test.eventsList.any { it.methodResult.exitCode() == 400 } },
        )
    }

    @Test
    fun `on_compute_phase_exit gets called without exception`() {
        onComputePhaseBaseTest(OnComputePhaseExitTestData.receiverNoThrow)
    }

    @Test
    fun `on_compute_phase_exit gets called with exception`() {
        onComputePhaseBaseTest(OnComputePhaseExitTestData.receiverThrow)
    }

    @Test
    fun `on_compute_phase_exit isn't called with exception when stopFirstError == true`() {
        val checkerContract = extractCheckerContractFromResource(OnComputePhaseExitStopOnFirstErrorTestData.checker)
        val senderContract = extractFuncContractFromResource(OnComputePhaseExitStopOnFirstErrorTestData.sender)
        val options = TvmOptions(stopOnFirstError = true)
        val tests =
            analyzeInterContract(
                listOf(checkerContract, senderContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        propertiesFound(
            tests,
            listOf { test -> test.eventsList.any { it.methodResult.exitCode() == 300 } },
        )
        checkInvariants(
            tests,
            listOf { test -> test.eventsList.all { it.methodResult.exitCode() !in listOf(400, 401) } },
        )
    }

    private fun onComputePhaseBaseTest(receiverContractPath: String) {
        val checkerContract = extractCheckerContractFromResource(OnComputePhaseExitTestData.checker)
        val senderContract = extractFuncContractFromResource(OnComputePhaseExitTestData.sender)
        val receiverContract = extractFuncContractFromResource(receiverContractPath)
        val communicationScheme = extractCommunicationSchemeFromResource(OnComputePhaseExitTestData.communicationScheme)
        val options = createIntercontractOptions(communicationScheme)
        val tests =
            analyzeInterContract(
                listOf(checkerContract, senderContract, receiverContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )
        propertiesFound(
            tests,
            listOf { test -> test.eventsList.any { it.methodResult.exitCode() == 400 } },
        )
    }

    private fun TvmMethodSymbolicResult.exitCode(): Int =
        when (this) {
            is TvmSuccessfulExecution -> exitCode
            is TvmMethodFailure -> exitCode
            else -> error("Soft failure in a test")
        }

    private fun extractCheckerContractFromResource(checkerResourcePath: String): TsaContractCode {
        val checkerPath = extractResource(checkerResourcePath)
        val checkerContract = getFuncContract(checkerPath, FIFT_STDLIB_RESOURCE, isTSAChecker = true)
        return checkerContract
    }

    private fun extractFuncContractFromResource(contractResourcePath: String): TsaContractCode {
        val contractPath = extractResource(contractResourcePath)
        val checkerContract = getFuncContract(contractPath, FIFT_STDLIB_RESOURCE)
        return checkerContract
    }

    private fun extractCommunicationSchemeFromResource(
        communicationSchemeResourcePath: String,
    ): Map<ContractId, TvmContractHandlers> {
        val communicationSchemePath = extractResource(communicationSchemeResourcePath)
        val communicationScheme = communicationSchemeFromJson(communicationSchemePath.readText())
        return communicationScheme
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
                val checkerExitCode = test.eventsList.single { it.id == 0 }.methodResult
                val checkerExitCodeGood = (checkerExitCode as? TvmMethodFailure)?.exitCode == 258
                val thereWereThreeFailedMethod =
                    test.eventsList.count { it.methodResult is TvmMethodFailure } == 3
                checkerExitCodeGood && thereWereThreeFailedMethod
            },
        )

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode != 257 },
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
                    (test.result as? TvmMethodFailure)?.exitCode != receivedBounceOnSoftFailureExitCode
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
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 255 },
        )
        checkInvariants(
            tests,
            listOf { test ->
                test.eventsList.mapNotNull { it.methodResult as? TvmMethodFailure }.all { failedResult ->
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
            enableOutMessageAnalysis = true,
            stopOnFirstError = false,
        )

    @Test
    fun intBlastOptimizationTest() {
        val pathTactConfig = extractResource(tactConfig)
        val checkerPath = extractResource(intBlastOptimizationChecker)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true,
            )
        val analyzedContract = getTactContract(TactSourcesDescription(pathTactConfig, "IntOptimization", "GuessGame"))

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
            )

        // There is at least one failed execution with exit code 257
        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 257 },
        )

        // All executions are either failed executions with exit code 257, or successful
        checkInvariants(
            tests,
            listOf { test ->
                val result = test.result
                if (result is TvmMethodFailure) {
                    result.exitCode == 257
                } else {
                    result is TvmSuccessfulExecution
                }
            },
        )
    }
}
