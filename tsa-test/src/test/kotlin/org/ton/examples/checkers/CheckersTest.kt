package org.ton.examples.checkers

import org.ton.TvmContractHandlers
import org.ton.bitstring.BitString
import org.ton.cell.Cell
import org.ton.communicationSchemeFromJson
import org.ton.test.utils.FIFT_STDLIB_RESOURCE
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
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
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmMethodSymbolicResult
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmTestInput
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CheckersTest {
    private val internalCallChecker = "/checkers/send_internal.fc"
    private val internalCallCheckerWithCapture = "/checkers/send_internal_with_capture.fc"
    private val externalCallCheckerWithCapture = "/checkers/send_external_with_capture.fc"
    private val balancePath = "/args/balance.fc"
    private val balanceExternalPath = "/args/balance_external.fc"
    private val getC4CheckerPath = "/checkers/get_c4.fc"
    private val emptyContractPath = "/empty_contract.fc"
    private val tactConfig = "/tact/tact.config.json"
    private val intBlastOptimizationChecker = "/checkers/int_optimization.fc"

    private object TransactionRollBackTestData {
        const val SENDER = "/checkers/transaction-rollback/sender-checker.fc"
        const val RECEIVER = "/checkers/transaction-rollback/receiver.fc"
    }

    private object OnOutMessageTestData {
        const val CHECKER = "/checkers/on-out-message-test/checker.fc"
        const val CHECKER_FAIL = "/checkers/on-out-message-test/checker_fail.fc"
        const val SENDER = "/checkers/on-out-message-test/sender.fc"
        const val RECEIVER = "/checkers/on-out-message-test/receiver.fc"
        const val SCHEME = "/checkers/on-out-message-test/communication-scheme.json"
    }

    private object OnComputePhaseExitTestData {
        const val CHECKER = "/checkers/on-compute-phase-exit-test/checker.fc"
        const val CHECKER_FAIL = "/checkers/on-compute-phase-exit-test/checker_fail.fc"
        const val SENDER = "/checkers/on-compute-phase-exit-test/sender.fc"
        const val RECEIVER_NO_THROW = "/checkers/on-compute-phase-exit-test/receiver-nothrow.fc"
        const val RECEIVER_THROW = "/checkers/on-compute-phase-exit-test/receiver-throw.fc"
        const val SCHEME = "/checkers/on-compute-phase-exit-test/communication-scheme.json"
    }

    private object OnComputePhaseExitStopOnFirstErrorTestData {
        const val CHECKER = "/checkers/on-compute-phase-exit-stop-on-first-error-test/checker.fc"
        const val SENDER = "/checkers/on-compute-phase-exit-stop-on-first-error-test/sender.fc"
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
        val checkerContract = extractCheckerContractFromResource(OnOutMessageTestData.CHECKER)
        val senderContract = extractFuncContractFromResource(OnOutMessageTestData.SENDER)
        val receiverContract = extractFuncContractFromResource(OnOutMessageTestData.RECEIVER)
        val communicationScheme = extractCommunicationSchemeFromResource(OnOutMessageTestData.SCHEME)
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
    fun `fail in on_out_message is global`() {
        val checkerContract = extractCheckerContractFromResource(OnOutMessageTestData.CHECKER_FAIL)
        val senderContract = extractFuncContractFromResource(OnOutMessageTestData.SENDER)
        val receiverContract = extractFuncContractFromResource(OnOutMessageTestData.RECEIVER)
        val communicationScheme = extractCommunicationSchemeFromResource(OnOutMessageTestData.SCHEME)
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
            listOf { test -> test.result.exitCode() == 300 },
        )
    }

    @Test
    fun `on_compute_phase_exit gets called without exception`() {
        onComputePhaseBaseTest(OnComputePhaseExitTestData.RECEIVER_NO_THROW)
    }

    @Test
    fun `on_compute_phase_exit gets called with exception`() {
        onComputePhaseBaseTest(OnComputePhaseExitTestData.RECEIVER_THROW)
    }

    @Test
    fun `on_compute_phase_exit isn't called with exception when stopFirstError == true`() {
        val checkerContract = extractCheckerContractFromResource(OnComputePhaseExitStopOnFirstErrorTestData.CHECKER)
        val senderContract = extractFuncContractFromResource(OnComputePhaseExitStopOnFirstErrorTestData.SENDER)
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
        val checkerContract = extractCheckerContractFromResource(OnComputePhaseExitTestData.CHECKER)
        val senderContract = extractFuncContractFromResource(OnComputePhaseExitTestData.SENDER)
        val receiverContract = extractFuncContractFromResource(receiverContractPath)
        val communicationScheme = extractCommunicationSchemeFromResource(OnComputePhaseExitTestData.SCHEME)
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

    @Test
    fun `fail in compute_phase_exit is global`() {
        val checkerContract = extractCheckerContractFromResource(OnComputePhaseExitTestData.CHECKER_FAIL)
        val senderContract = extractFuncContractFromResource(OnComputePhaseExitTestData.SENDER)
        val receiverContract = extractFuncContractFromResource(OnComputePhaseExitTestData.RECEIVER_NO_THROW)
        val communicationScheme = extractCommunicationSchemeFromResource(OnComputePhaseExitTestData.SCHEME)
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
            listOf { test -> test.result.exitCode() == 300 },
        )
    }

    private fun TvmMethodSymbolicResult.exitCode(): Int =
        when (this) {
            is TvmSuccessfulExecution -> exitCode
            is TvmMethodFailure -> exitCode
            else -> error("Soft failure in a test")
        }

    private fun extractCommunicationSchemeFromResource(
        communicationSchemeResourcePath: String,
    ): Map<ContractId, TvmContractHandlers> {
        val communicationSchemePath = extractResource(communicationSchemeResourcePath)
        val communicationScheme = communicationSchemeFromJson(communicationSchemePath.readText())
        return communicationScheme
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

    @Test
    fun intBlastOptimizationTest() {
        val checkerContract = extractCheckerContractFromResource(intBlastOptimizationChecker)
        val pathTactConfig = extractResource(tactConfig)
        val analyzedContract = getTactContract(TactSourcesDescription(pathTactConfig, "IntOptimization", "GuessGame"))

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = TvmOptions(timeout = 120.seconds),
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
