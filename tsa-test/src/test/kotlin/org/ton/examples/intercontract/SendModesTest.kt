package org.ton.examples.intercontract

import org.ton.cell.CellBuilder
import org.ton.cell.buildCell
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.executionCode
import org.ton.test.utils.exitCode
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractCommunicationSchemeFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.state.INVALID_DST_ADDRESS_IN_OUTBOUND_MESSAGE
import org.usvm.machine.state.INVALID_SRC_ADDRESS_IN_OUTBOUND_MESSAGE
import org.usvm.machine.state.InsufficientFunds
import org.usvm.machine.state.TvmBadDestinationAddress
import org.usvm.machine.state.TvmDoubleSendRemainingValue
import org.usvm.test.resolver.TvmExecutionWithSoftFailure
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmTestFailure
import org.usvm.test.resolver.exitCode
import kotlin.test.Test
import kotlin.test.assertTrue

private infix fun Boolean.implies(other: Boolean) = this.not() || other

class SendModesTest {
    private val remainingBalanceContract = "/intercontract/modes/send_remaining_balance.fc"
    private val remainingBalanceWithAnotherMessageContract =
        "/intercontract/modes/send_remaining_balance_with_another_message.fc"
    private val remainingBalanceChecker = "/intercontract/modes/remaining_balance.fc"
    private val remainingBalanceNewSender = "/intercontract/modes/remaining-balance/sender.fc"
    private val remainingBalanceNewChecker = "/intercontract/modes/remaining-balance/checker.fc"
    private val remainingBalanceNewRecipient = "/intercontract/modes/remaining-balance/recipient.fc"
    private val remainingBalanceNewCommunicationScheme = "/intercontract/modes/remaining-balance/inter-contract.json"
    private val remainingValueContract = "/intercontract/modes/send_remaining_value.fc"
    private val remainingValueOpcode500Contract = "/intercontract/modes/send_remaining_value_opcode_500.fc"
    private val remainingValueDoubleContract = "/intercontract/modes/send_remaining_value_double.fc"
    private val remainingValueChecker = "/intercontract/modes/remaining_value.fc"
    private val remainingValueOf2ndChecker = "/intercontract/modes/remaining_value_checker_of_2nd.fc"
    private val ignoreErrorsContract = "/intercontract/modes/send_ignore_error_sender.fc"
    private val ignoreErrorsChecker = "/intercontract/modes/send_ignore_error_checker.fc"
    private val ignoreErrorTestScheme = "/intercontract/modes/ignore_error_test_scheme.json"
    private val recipientBouncePath = "/bounce/receive_bounce_msg.fc"
    private val sendRemainingValueNotFromCheckerCommunicationScheme =
        "/intercontract/modes/send-remaining-value-with-2nd-scheme.json"
    private val valueChecker = "/intercontract/modes/value_checker.fc"
    private val valueContract = "/intercontract/modes/value_contract.fc"
    private val sendRemainingValueChecker = "/intercontract/modes/remaining-value/checker.fc"
    private val sendRemainingValueSender = "/intercontract/modes/remaining-value/sender.fc"
    private val sendRemainingValueRecipient = "/intercontract/modes/remaining-value/recipient.fc"
    private val sendRemainingValueCommunicationScheme =
        "/intercontract/modes/remaining-value/inter-contract.json"
    private val sendPayForwardFeesSeparatelyChecker = "/intercontract/modes/pay-forward-fees-separately/checker.fc"
    private val sendPayForwardFeesSeparatelySender = "/intercontract/modes/pay-forward-fees-separately/sender.fc"
    private val sendPayForwardFeesSeparatelyRecipient = "/intercontract/modes/pay-forward-fees-separately/recipient.fc"
    private val sendPayForwardFeesSeparatelyCommunicationScheme =
        "/intercontract/modes/pay-forward-fees-separately/inter-contract.json"

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
            listOf { test -> test.exitCode() == 257 },
        )
    }

    @Test
    fun `SendRemainingBalance pure`() {
        sendRemainingBalanceBaseTest(100, listOf(10000))
    }

    @Test
    fun `SendRemainingBalance with PayForwardFeesSeparately`() {
        sendRemainingBalanceBaseTest(200, listOf(10000))
    }

    @Test
    fun `SendRemainingBalance after normal message`() {
        sendRemainingBalanceBaseTest(300, listOf(10000))
    }

    @Test
    fun `SendRemainingBalance plus SendRemainingValue`() {
        sendRemainingBalanceBaseTest(400, listOf(34))
    }

    @Test
    fun `normal message after SendRemainingBalance`() {
        sendRemainingBalanceBaseTest(500, listOf(37))
    }

    private fun sendRemainingBalanceBaseTest(
        opcode: Int,
        expectedOpCodes: List<Int>,
    ) {
        val checkerContract = extractCheckerContractFromResource(remainingBalanceNewChecker)
        val analyzedSender = extractFuncContractFromResource(remainingBalanceNewSender)
        val analyzedReceiver = extractFuncContractFromResource(remainingBalanceNewRecipient)
        val communicationScheme = extractCommunicationSchemeFromResource(remainingBalanceNewCommunicationScheme)

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
                        TvmConcreteContractData(contractC4 = CellBuilder.beginCell().storeInt(opcode, 64).endCell()),
                        TvmConcreteContractData(),
                        TvmConcreteContractData(),
                    ),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        propertiesFound(
            tests,
            expectedOpCodes.map { expectedOpcode ->
                { test: TvmSymbolicTest -> test.executionCode() == expectedOpcode }
            },
        )

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode !in 201..3001 },
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
                    val methodResult = it.actionPhaseResult
                    methodResult is TvmTestFailure && methodResult.exitCode == 37
                }
            },
        )
    }

    @Test
    fun `test sendRemainingValue with 0 sent nanotons`() {
        sendRemainingValueTest(0)
    }

    @Test
    fun `test sendRemainingValue with 10 sent nanoton`() {
        sendRemainingValueTest(10)
    }

    private fun sendRemainingValueTest(sentGrams: Int) {
        val checkerContract = extractCheckerContractFromResource(remainingValueChecker)
        val analyzedContract = extractFuncContractFromResource(remainingValueContract)

        val concreteC4 =
            CellBuilder.createCell {
                val prefix = 4
                storeUInt(prefix, 4)
                storeUInt(sentGrams, prefix * 15)
            }

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = TvmOptions(stopOnFirstError = true, enableOutMessageAnalysis = true),
                concreteContractData =
                    List(2) {
                        TvmConcreteContractData(contractC4 = concreteC4)
                    },
            )

        checkInvariants(
            tests,
            listOf { test ->
                test.exitCode() == 257 || test.exitCode() == 37
            },
        )

        propertiesFound(
            tests,
            listOf { test -> test.exitCode() == 257 },
        )
    }

    @Test
    fun sendPayForwardFeesSeparatelyPure() {
        payForwardFeesSeparatelyBaseTest(100)
    }

    @Test
    fun sendPayForwardFeesSeparatelyWithSendRemainingBalance() {
        payForwardFeesSeparatelyBaseTest(200)
    }

    @Test
    fun sendPayForwardFeesSeparatelyWithSendRemainingValue() {
        payForwardFeesSeparatelyBaseTest(300)
    }

    private fun payForwardFeesSeparatelyBaseTest(opcode: Int) {
        val checkerContract = extractCheckerContractFromResource(sendPayForwardFeesSeparatelyChecker)
        val analyzedSender = extractFuncContractFromResource(sendPayForwardFeesSeparatelySender)
        val analyzedReceiver = extractFuncContractFromResource(sendPayForwardFeesSeparatelyRecipient)
        val communicationScheme =
            extractCommunicationSchemeFromResource(sendPayForwardFeesSeparatelyCommunicationScheme)

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
                        TvmConcreteContractData(contractC4 = CellBuilder.beginCell().storeInt(opcode, 64).endCell()),
                        TvmConcreteContractData(),
                        TvmConcreteContractData(),
                    ),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode == 10000 },
        )

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode !in 201..3001 },
        )
    }

    @Test
    fun `approximate balance calculation with SendFwdFeesSeparately`() {
        val checkerContract = extractCheckerContractFromResource(valueChecker)
        val analyzedContract = extractFuncContractFromResource(valueContract)

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
            listOf { test -> test.exitCode() != 257 },
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
                                    sendRemainingValueNotFromCheckerCommunicationScheme,
                                ),
                            ),
                    ),
            )

        assertTrue { tests.isNotEmpty() }

        propertiesFound(
            tests,
            listOf { test -> test.exitCode() == 258 },
        )
        propertiesFound(
            tests,
            listOf { test -> test.exitCode() == 257 },
        )
    }

    @Test
    fun sendRemainingValueDoubleTest() {
        val checkerContract = extractCheckerContractFromResource(remainingValueChecker)
        val analyzedContract = extractFuncContractFromResource(remainingValueDoubleContract)

        val concreteContractData =
            TvmConcreteContractData(
                contractC4 = buildCell { storeUInt(0, 4) },
            )

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = TvmOptions(stopOnFirstError = true, enableOutMessageAnalysis = true),
                concreteContractData = listOf(concreteContractData, concreteContractData),
            )

        assertTrue { tests.isNotEmpty() }
        checkInvariants(
            tests,
            listOf { test ->
                val methodResult = test.result
                methodResult is TvmExecutionWithSoftFailure &&
                    methodResult.failure.exit is TvmDoubleSendRemainingValue
            },
        )
    }

    @Test
    fun `SendIgnoreError invalid source address`() {
        sendIgnoreErrorBaseTest(100)
    }

    @Test
    fun `SendIgnoreError invalid destination address`() {
        sendIgnoreErrorBaseTest(101, expectedSoftFailure = true)
    }

    @Test
    fun `SendIgnoreError not enough Toncoin`() {
        sendIgnoreErrorBaseTest(102)
    }

    @Test
    fun `SendIgnoreError good message between two ignored messages`() {
        sendIgnoreErrorBaseTest(105, shouldSendSomething = true)
    }

    private fun sendIgnoreErrorBaseTest(
        opcode: Int,
        shouldSendSomething: Boolean = false,
        expectedSoftFailure: Boolean = false,
    ) {
        val checkerContract = extractCheckerContractFromResource(ignoreErrorsChecker)
        val analyzedSender = extractFuncContractFromResource(ignoreErrorsContract)
        val analyzedRecipient = extractFuncContractFromResource(recipientBouncePath)
        val communicationScheme = extractCommunicationSchemeFromResource(ignoreErrorTestScheme)

        val options =
            TvmOptions(
                intercontractOptions = IntercontractOptions(communicationScheme = communicationScheme),
                enableOutMessageAnalysis = true,
                stopOnFirstError = false,
            )

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedSender, analyzedRecipient),
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(contractC4 = CellBuilder().storeInt(opcode, 64).endCell()),
                        TvmConcreteContractData(),
                        TvmConcreteContractData(),
                    ),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        if (!expectedSoftFailure) {
            checkInvariants(
                tests,
                listOf(
                    { test -> test.eventsList.all { it.actionPhaseResult?.exitCode() != InsufficientFunds.EXIT_CODE } },
                    { test ->
                        test.eventsList.all {
                            it.actionPhaseResult?.exitCode() != INVALID_DST_ADDRESS_IN_OUTBOUND_MESSAGE
                        }
                    },
                    { test ->
                        test.eventsList.all {
                            it.actionPhaseResult?.exitCode() != INVALID_SRC_ADDRESS_IN_OUTBOUND_MESSAGE
                        }
                    },
                ),
            )
            propertiesFound(
                tests,
                listOf { test ->
                    val recipientReceivedTheMessage = test.eventsList.any { it.contractId == 2 }
                    val recipientDidntReceiveTheMessage = test.eventsList.all { it.contractId != 2 }
                    test.exitCode() == 500 &&
                        shouldSendSomething implies recipientReceivedTheMessage &&
                        shouldSendSomething.not() implies recipientDidntReceiveTheMessage
                },
            )
        } else {
            checkInvariants(
                tests,
                listOf { test ->
                    val result = test.result
                    result is TvmExecutionWithSoftFailure && result.failure.exit is TvmBadDestinationAddress
                },
            )
        }
    }

    private fun sendRemainingValueNewBaseTest(opcode: Int) {
        val checkerContract = extractCheckerContractFromResource(sendRemainingValueChecker)
        val analyzedSender = extractFuncContractFromResource(sendRemainingValueSender)
        val analyzedReceiver = extractFuncContractFromResource(sendRemainingValueRecipient)
        val communicationScheme =
            extractCommunicationSchemeFromResource(sendRemainingValueCommunicationScheme)

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
                        TvmConcreteContractData(contractC4 = CellBuilder.beginCell().storeInt(opcode, 64).endCell()),
                        TvmConcreteContractData(),
                        TvmConcreteContractData(),
                    ),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode == 10000 },
        )

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode !in 201..3001 },
        )
    }

    @Test
    fun sendRemainingValuePurely() {
        sendRemainingValueNewBaseTest(100)
    }

    @Test
    fun sendRemainingValueAfterBaseMessage() {
        sendRemainingValueNewBaseTest(200)
    }

    @Test
    fun sendRemainingValueWithPayForwardFeesSeparately() {
        sendRemainingValueNewBaseTest(300)
    }
}
