package org.ton.examples.checkers

import org.ton.test.utils.FIFT_STDLIB_RESOURCE
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TvmContext
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import kotlin.test.Test
import kotlin.test.assertTrue

class Statements {
    private val targetContract = "/checkers/statements/target_contract.fc"
    private val sendInRepeatChecker1 = "/checkers/statements/send_in_repeat_checker1.fc"
    private val sendInRepeatChecker2 = "/checkers/statements/send_in_repeat_checker2.fc"
    private val sendInRepeatChecker3 = "/checkers/statements/send_in_repeat_checker3.fc"

    private fun checkFailedSendInStatement(checkerPathStr: String, numberOfMessages: Int) {
        val analyzedPath = extractResource(targetContract)
        val checkerPath = extractResource(checkerPathStr)

        val checkerContract = getFuncContract(
            checkerPath,
            FIFT_STDLIB_RESOURCE,
            isTSAChecker = true
        )
        val analyzedContract = getFuncContract(analyzedPath, FIFT_STDLIB_RESOURCE)

        val tests = analyzeInterContract(
            listOf(checkerContract, analyzedContract),
            startContractId = 0,
            methodId = TvmContext.RECEIVE_INTERNAL_ID
        )

        propertiesFound(
            tests,
            listOf { test ->
                val result = test.result
                // The result must be a failure with exit code 256
                if (result is TvmMethodFailure) {
                    assertTrue(result.exitCode == 256)
                    val inputs = test.additionalInputs
                    // Assert that the correct number of messages were sent
                    assertTrue(inputs.size == numberOfMessages)
                    // All found messages must contain in their message body the 102 opcode in the first 32 bits
                    inputs.map { input -> input.value.msgBody.cell.data }.all { cellData ->
                        val opCodeStr = cellData.slice(0..31)
                        opCodeStr.toInt(2) == 102
                    }
                } else {
                    false
                }
            }
        )
    }

    private fun checkSuccessfulSendInStatement(checkerPathStr: String) {
        val analyzedPath = extractResource(targetContract)
        val checkerPath = extractResource(checkerPathStr)

        val checkerContract = getFuncContract(
            checkerPath,
            FIFT_STDLIB_RESOURCE,
            isTSAChecker = true
        )
        val analyzedContract = getFuncContract(analyzedPath, FIFT_STDLIB_RESOURCE)

        val tests = analyzeInterContract(
            listOf(checkerContract, analyzedContract),
            startContractId = 0,
            methodId = TvmContext.RECEIVE_INTERNAL_ID
        )

        checkInvariants(
            tests,
            listOf { test -> test.result is TvmSuccessfulExecution }
        )
    }

    @Test
    fun checkSendInRepeat1() {
        // This test sends 1 message to the target, inside a repeat with a single iteration
        checkFailedSendInStatement(sendInRepeatChecker1, 1);
    }

    @Test
    fun checkSendInRepeat2() {
        // This test sends 2 messages to the target, inside a repeat with 2 iterations.
        checkFailedSendInStatement(sendInRepeatChecker2, 2);
    }

    @Test
    fun checkSendInRepeat3() {
        // This test sends 4 messages to the target, inside a repeat with 4 iterations.
        // 4 iterations or more reaches the limit in which TSA unfolds loops. As such, there shouldn't be
        // failures in the test results.
        checkSuccessfulSendInStatement(sendInRepeatChecker3);
    }
}