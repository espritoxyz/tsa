package org.ton.examples.checkers

import org.ton.TvmContractHandlers
import org.ton.bitstring.BitString
import org.ton.bytecode.TsaContractCode
import org.ton.cell.Cell
import org.ton.communicationSchemeFromJson
import org.ton.test.utils.FIFT_STDLIB_RESOURCE
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.getAddressBits
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
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmTestInput
import kotlin.io.path.readText
import kotlin.test.Ignore
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
    private val remainingBalanceContract = "/args/send_remaining_balance.fc"
    private val remainingBalanceChecker = "/checkers/remaining_balance.fc"
    private val remainingValueContract = "/args/send_remaining_value.fc"
    private val remainingValueChecker = "/checkers/remaining_value.fc"
    private val tactConfig = "/tact/tact.config.json"
    private val intBlastOptimizationChecker = "/checkers/int_optimization.fc"
    private val ignoreErrorsContract = "/args/send_ignore_error_flag.fc"
    private val ignoreErrorsChecker = "/checkers/ignore_error.fc"
    private val ignoreErrorTestScheme = "/checkers/ignore_error_test_scheme.json"
    private val bounceFormatContract = "/args/bounce_format_send.fc"
    private val bounceFormatChecker = "/checkers/bounce_format.fc"
    private val bounceFormatScheme = "/checkers/bounce_format_scheme.json"
    private val intercontractConsistencySender = "/args/inter-contract-consistency.fc"
    private val intercontractConsistencyRecepient = "/args/inter-contract-consistency-recepient.fc"
    private val intercontractConsistencyChecker = "/checkers/inter-contract-consistency-checker.fc"
    private val intercontractConsistencyScheme = "/checkers/inter-contract-consistency.json"

    private object TransactionRollBackTestData {
        const val SENDER = "/checkers/transaction-rollback/sender-checker.fc"
        const val RECEIVER = "/checkers/transaction-rollback/receiver.fc"
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
            balanceExternalPath
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
                isTSAChecker = true
            )
        val analyzedContract = getFuncContract(path, FIFT_STDLIB_RESOURCE)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID
            )

        propertiesFound(
            tests,
            listOf(
                { test -> test.result is TvmSuccessfulExecution },
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1001 }
            )
        )

        checkInvariants(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 1000 },
                additionalCheck
            )
        )

        val successfulTests = tests.filter { it.result is TvmSuccessfulExecution }
        checkInvariants(
            successfulTests,
            listOf { test -> fetchedKeys.all { it in test.fetchedValues } }
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
                isTSAChecker = true
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
                        TvmConcreteContractData(contractC4 = Cell(BitString.of("00000100")))
                    )
            )

        assertTrue { tests.isNotEmpty() }

        checkInvariants(
            tests,
            listOf { test -> test.result is TvmSuccessfulExecution }
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
                        TvmConcreteContractData(contractC4 = Cell(BitString.of("0")))
                    )
            )

        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 258 }
        )

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode != 257 }
        )
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
                        TvmConcreteContractData()
                    )
            )

        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 258 }
        )

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode != 257 }
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
                        TvmConcreteContractData()
                    )
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
            }
        )
    }

    @Test
    fun bounceFormatTest() {
        val checkerContract = extractCheckerContractFromResource(bounceFormatChecker)
        val analyzedSender = extractFuncContractFromResource(bounceFormatContract)
        val analyzedRecepient = extractFuncContractFromResource(recipientBouncePath)
        val communicationScheme = extractCommunicationSchemeFromResource(bounceFormatScheme)
        val options = createIntercontractOptions(communicationScheme)
        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedSender, analyzedRecepient),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(),
                        TvmConcreteContractData(contractC4 = Cell(BitString.of("0"))),
                        TvmConcreteContractData()
                    )
            )

        propertiesFound(
            tests,
            // the target contract should change its persistent data
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 255 }
        )
        // TODO: adjust the test to disallow the given intermediate exit codes
        // when event logging will be supported
        checkInvariants(
            tests,
            listOf(
                // see bounce_format_send.fc
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 257 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 258 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 259 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 260 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 261 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 262 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 263 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 264 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 265 }
            )
        )
    }

    private fun createIntercontractOptions(communicationScheme: Map<ContractId, TvmContractHandlers>): TvmOptions =
        TvmOptions(
            intercontractOptions =
                IntercontractOptions(
                    communicationScheme = communicationScheme
                ),
            enableOutMessageAnalysis = true,
            stopOnFirstError = false
        )

    @Ignore("SendRemainingBalance mode is not supported")
    @Test
    fun sendRemainingBalanceTest() {
        val pathContract = extractResource(remainingBalanceContract)
        val checkerPath = extractResource(remainingBalanceChecker)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true
            )
        val analyzedContract = getFuncContract(pathContract, FIFT_STDLIB_RESOURCE)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID
            )

        assertTrue { tests.isNotEmpty() }

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 257 }
        )
    }

    @Ignore("SendRemainingValue mode is not supported")
    @Test
    fun sendRemainingValueTest() {
        val pathContract = extractResource(remainingValueContract)
        val checkerPath = extractResource(remainingValueChecker)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true
            )
        val analyzedContract = getFuncContract(pathContract, FIFT_STDLIB_RESOURCE)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID
            )

        assertTrue { tests.isNotEmpty() }

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 257 }
        )
    }

    @Test
    fun intBlastOptimizationTest() {
        val pathTactConfig = extractResource(tactConfig)
        val checkerPath = extractResource(intBlastOptimizationChecker)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true
            )
        val analyzedContract = getTactContract(TactSourcesDescription(pathTactConfig, "IntOptimization", "GuessGame"))

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID
            )

        // There is at least one failed execution with exit code 257
        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 257 }
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
            }
        )
    }

    @Ignore("SendIgnoreError flag is not supported")
    @Test
    fun sendIgnoreErrorTest() {
        val pathSender = extractResource(ignoreErrorsContract)
        val pathRecepient = extractResource(recipientBouncePath)
        val checkerPath = extractResource(ignoreErrorsChecker)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true
            )
        val analyzedSender = getFuncContract(pathSender, FIFT_STDLIB_RESOURCE)
        val analyzedRecepient = getFuncContract(pathRecepient, FIFT_STDLIB_RESOURCE)

        val communicationSchemePath = extractResource(ignoreErrorTestScheme)
        val communicationScheme = communicationSchemeFromJson(communicationSchemePath.readText())

        val options =
            TvmOptions(
                intercontractOptions =
                    IntercontractOptions(
                        communicationScheme = communicationScheme
                    ),
                enableOutMessageAnalysis = true
            )

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedSender, analyzedRecepient),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options
            )

        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 258 }
        )

        checkInvariants(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 35 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 36 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 37 }
            )
        )
    }

    @Ignore("Consistency is not met")
    @Test
    fun intercontractConsistencyTest() {
        val pathSender = extractResource(intercontractConsistencySender)
        val pathRecepient = extractResource(intercontractConsistencyRecepient)
        val checkerPath = extractResource(intercontractConsistencyChecker)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true
            )
        val analyzedSender = getFuncContract(pathSender, FIFT_STDLIB_RESOURCE)
        val analyzedRecepient = getFuncContract(pathRecepient, FIFT_STDLIB_RESOURCE)

        val communicationSchemePath = extractResource(intercontractConsistencyScheme)
        val communicationScheme = communicationSchemeFromJson(communicationSchemePath.readText())

        val options =
            TvmOptions(
                intercontractOptions =
                    IntercontractOptions(
                        communicationScheme = communicationScheme
                    ),
                enableOutMessageAnalysis = true
            )

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedSender, analyzedRecepient),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(),
                        TvmConcreteContractData(),
                        TvmConcreteContractData(
                            addressBits =
                                getAddressBits(
                                    "0:fd38d098511c43015e02cd185cfcac3befffa89a2a7f20d65440638a9475b9db"
                                )
                        )
                    )
            )

        assertTrue { tests.isNotEmpty() }

        checkInvariants(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 257 },
                { test ->
                    ((((test.additionalInputs[0] as? TvmTestInput.RecvInternalInput)?.msgBody)?.cell)?.data)
                        ?.startsWith(
                            "00000000000000000000000001100100" +
                                getAddressBits("0:fd38d098511c43015e02cd185cfcac3befffa89a2a7f20d65440638a9475b9db")
                        ) == true
                }
            )
        )
    }
}
