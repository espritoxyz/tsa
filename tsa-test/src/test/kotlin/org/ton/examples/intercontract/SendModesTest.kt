package org.ton.examples.intercontract

import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractCommunicationSchemeFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.test.resolver.TvmMethodFailure
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class SendModesTest {
    private val remainingBalanceContract = "/intercontract/modes/send_remaining_balance.fc"
    private val remainingBalanceWithAnotherMessageContract =
        "/intercontract/modes/send_remaining_balance_with_another_message.fc"
    private val remainingBalanceChecker = "/intercontract/modes/remaining_balance.fc"
    private val remainingValueContract = "/intercontract/modes/send_remaining_value.fc"
    private val remainingValueOpcode500Contract = "/intercontract/modes/send_remaining_value_opcode_500.fc"
    private val remainingValueDoubleContract = "/intercontract/modes/send_remaining_value_double.fc"
    private val remainingValueChecker = "/intercontract/modes/remaining_value.fc"
    private val remainingValueOf2ndChecker = "/intercontract/modes/remaining_value_checker_of_2nd.fc"
    private val ignoreErrorsContract = "/intercontract/modes/send_ignore_error_flag.fc"
    private val ignoreErrorsChecker = "/intercontract/modes/ignore_error.fc"
    private val ignoreErrorTestScheme = "/intercontract/modes/ignore_error_test_scheme.json"
    private val recipientBouncePath = "/args/receive_bounce_msg.fc"

    @Test
    fun sendRemainingBalanceTest() {
        val checkerContract = extractCheckerContractFromResource(remainingBalanceChecker)
        val analyzedContract = extractFuncContractFromResource(remainingBalanceContract)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = TvmOptions(stopOnFirstError = false, enableOutMessageAnalysis = true),
            )

        assertTrue { tests.isNotEmpty() }

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 257 },
        )
    }

    @Test
    fun `messages cannot be sent after sending with SendRemainingBalance`() {
        val checkerContract = extractCheckerContractFromResource(remainingBalanceChecker)
        val analyzedContract = extractFuncContractFromResource(remainingBalanceWithAnotherMessageContract)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = TvmOptions(stopOnFirstError = false, enableOutMessageAnalysis = true),
            )

        assertTrue { tests.isNotEmpty() }
        checkInvariants(
            tests,
            listOf { test ->
                test.eventsList.any {
                    val methodResult = it.methodResult
                    methodResult is TvmMethodFailure && methodResult.exitCode == 37
                }
            },
        )
    }

    @Test
    fun sendRemainingValueTest() {
        val checkerContract = extractCheckerContractFromResource(remainingValueChecker)
        val analyzedContract = extractFuncContractFromResource(remainingValueContract)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = TvmOptions(stopOnFirstError = false, enableOutMessageAnalysis = true),
            )

        assertTrue { tests.isNotEmpty() }

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 257 },
        )
    }

    @Test
    fun `sendRemainingValue sent not from checker`() {
        val checkerContract = extractCheckerContractFromResource(remainingValueOf2ndChecker)
        val analyzedSenderContract = extractFuncContractFromResource(remainingValueContract)
        val analyzedRecipientContract = extractFuncContractFromResource(remainingValueOpcode500Contract)
        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedSenderContract, analyzedRecipientContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options =
                    TvmOptions(
                        stopOnFirstError = false,
                        enableOutMessageAnalysis = true,
                        intercontractOptions =
                            IntercontractOptions(
                                extractCommunicationSchemeFromResource(
                                    "/intercontract/modes/send-remaining-value-with-2nd-scheme.json",
                                ),
                            ),
                    ),
            )

        assertTrue { tests.isNotEmpty() }

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 257 },
        )
    }

    @Test
    fun sendRemainingValueDoubleTest() {
        val checkerContract = extractCheckerContractFromResource(remainingValueChecker)
        val analyzedContract = extractFuncContractFromResource(remainingValueDoubleContract)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = TvmOptions(stopOnFirstError = false, enableOutMessageAnalysis = true),
            )

        assertTrue { tests.isNotEmpty() }

        checkInvariants(
            tests,
            listOf { test ->
                test.eventsList.any {
                    val methodResult = it.methodResult
                    methodResult is TvmMethodFailure && methodResult.exitCode == 37
                }
            },
        )
    }

    @Ignore("SendIgnoreError flag is not supported")
    @Test
    fun sendIgnoreErrorTest() {
        val checkerContract = extractCheckerContractFromResource(ignoreErrorsChecker)
        val analyzedSender = extractFuncContractFromResource(ignoreErrorsContract)
        val analyzedRecipient = extractFuncContractFromResource(recipientBouncePath)
        val communicationScheme = extractCommunicationSchemeFromResource(ignoreErrorTestScheme)

        val options =
            TvmOptions(
                intercontractOptions = IntercontractOptions(communicationScheme = communicationScheme),
                enableOutMessageAnalysis = true,
            )

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedSender, analyzedRecipient),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 258 },
        )

        checkInvariants(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 35 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 36 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode != 37 },
            ),
        )
    }
}
