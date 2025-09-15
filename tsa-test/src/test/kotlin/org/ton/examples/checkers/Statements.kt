package org.ton.examples.checkers

import org.ton.test.utils.FIFT_STDLIB_RESOURCE
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmTestInput.RecvInternalInput
import kotlin.test.Test
import kotlin.test.assertTrue

private const val LOOP_ITERATION_LIMIT = 2
private const val EXIT_CODE = 256

class Statements {
    private val targetContract = "/checkers/statements/target_contract.fc"
    private val sendInRepeatChecker1 = "/checkers/statements/send_in_repeat_checker1.fc"
    private val sendInRepeatChecker2 = "/checkers/statements/send_in_repeat_checker2.fc"
    private val sendInRepeatChecker3 = "/checkers/statements/send_in_repeat_checker3.fc"
    private val sendInRepeatChecker4 = "/checkers/statements/send_in_repeat_checker4.fc"
    private val sendInWhileChecker1 = "/checkers/statements/send_in_while_checker1.fc"
    private val sendInWhileChecker2 = "/checkers/statements/send_in_while_checker2.fc"
    private val sendInWhileChecker3 = "/checkers/statements/send_in_while_checker3.fc"
    private val sendInUntilChecker1 = "/checkers/statements/send_in_until_checker1.fc"
    private val sendInUntilChecker2 = "/checkers/statements/send_in_until_checker2.fc"
    private val sendInUntilChecker3 = "/checkers/statements/send_in_until_checker3.fc"
    private val sendInUntilChecker4 = "/checkers/statements/send_in_until_checker4.fc"

    private fun extractOpcode(cellData: String): Int {
        val opCodeStr = cellData.slice(0..31)
        return opCodeStr.toInt(2)
    }

    private fun checkFailedSendInStatement(
        checkerPathStr: String,
        possibleOpcodesList: List<List<Int>>,
    ) {
        val analyzedPath = extractResource(targetContract)
        val checkerPath = extractResource(checkerPathStr)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true,
            )
        val analyzedContract = getFuncContract(analyzedPath, FIFT_STDLIB_RESOURCE)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = TvmOptions(loopIterationLimit = LOOP_ITERATION_LIMIT),
            )

        checkInvariants(
            tests,
            listOf { test ->
                val result = test.result
                // The result must be a failure with exit code EXIT_CODE
                if (result is TvmMethodFailure) {
                    if (result.exitCode != EXIT_CODE) {
                        return@listOf false
                    }
                    val inputs = test.additionalInputs
                    // This list contains the opcode for each of the discovered messages, in that order
                    val foundOpcodes =
                        inputs
                            .map { input ->
                                input.value as RecvInternalInput
                            }.map { input ->
                                extractOpcode(input.msgBody.cell.data)
                            }
                    // At least one of the possible opcode lists must coincide with the discovered opcodes
                    possibleOpcodesList.any { possibleOpcodes -> possibleOpcodes == foundOpcodes }
                } else {
                    result is TvmSuccessfulExecution
                }
            },
        )

        // There must exist at least one test that produced error code EXIT_CODE
        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == EXIT_CODE },
        )
    }

    private fun checkNoRunsSendInStatement(checkerPathStr: String) {
        val analyzedPath = extractResource(targetContract)
        val checkerPath = extractResource(checkerPathStr)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true,
            )
        val analyzedContract = getFuncContract(analyzedPath, FIFT_STDLIB_RESOURCE)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = TvmOptions(loopIterationLimit = LOOP_ITERATION_LIMIT),
            )

        // There should not exist a test
        assertTrue(tests.isEmpty())
    }

    @Test
    fun checkSendInRepeat1() {
        /* This test sends 1 message to the target, inside a repeat with a single iteration.
           The only possibility is that the message sent has opcode 102.
           Therefore, our possible opcodes list just contains:
            [102]   A single list with expected opcode for the only message
         */
        checkFailedSendInStatement(sendInRepeatChecker1, listOf(listOf(102)))
    }

    @Test
    fun checkSendInRepeat2() {
        /* This test sends 2 messages to the target, inside a repeat with 2 iterations.
           The only possibility is that the first message sends opcode 102, but the second message could send opcodes 102 or 103
           and the assertion would still be true.
           Therefore, our possible opcodes list contains:
           [102, 103]   <-- The first message sends opcode 102, the second message 103
           [102, 102]   <-- Both messages send opcode 102
         */
        checkFailedSendInStatement(sendInRepeatChecker2, listOf(listOf(102, 103), listOf(102, 102)))
    }

    @Test
    fun checkSendInRepeat3() {
        /* This test sends 3 messages to the target, inside a repeat with 3 iterations.
           The only possibility is that the first message sends opcode 102, but the second and third messages could send opcodes 102 or 103
           and the assertion would still be true.
           Therefore, our possible opcodes list contains:
           [102, 103, 103]   <-- The first message sends opcode 102, the second and third 103
           [102, 103, 102]   <-- The first message sends opcode 102, the second 103 and third 102
           [102, 102, 103]   <-- The first and second messages send opcode 102, the third 103
           [102, 102, 102]   <-- All messages send opcode 102
         */
        checkFailedSendInStatement(
            sendInRepeatChecker3,
            listOf(
                listOf(102, 103, 103),
                listOf(102, 103, 102),
                listOf(102, 102, 103),
                listOf(102, 102, 102),
            ),
        )
    }

    @Test
    fun checkSendInRepeat4() {
        // This test sends 4 messages to the target, inside a repeat with 4 iterations.
        // 4 iterations or more reaches the limit in which TSA unfolds repeats. As such, there shouldn't exist
        // runs generated by TSA.
        checkNoRunsSendInStatement(sendInRepeatChecker4)
    }

    @Test
    fun checkSendInWhile1() {
        /* This test sends 1 message to the target, inside a while with a single iteration.
           Explanation is identical to test "checkSendInRepeat1"
         */
        checkFailedSendInStatement(sendInWhileChecker1, listOf(listOf(102)))
    }

    @Test
    fun checkSendInWhile2() {
        /* This test sends 2 messages to the target, inside a while with 2 iterations.
           Explanation is identical to test "checkSendInRepeat2"
         */
        checkFailedSendInStatement(sendInWhileChecker2, listOf(listOf(102, 103), listOf(102, 102)))
    }

    @Test
    fun checkSendInWhile3() {
        // This test sends 3 messages to the target, inside a while with 3 iterations.
        // 3 iterations or more reaches the limit in which TSA unfolds whiles. As such, there shouldn't exist
        // runs generated by TSA.
        checkNoRunsSendInStatement(sendInWhileChecker3)
    }

    @Test
    fun checkSendInUntil1() {
        /* This test sends 1 message to the target, inside an until with a single iteration.
           Explanation is identical to test "checkSendInRepeat1"
         */
        checkFailedSendInStatement(sendInUntilChecker1, listOf(listOf(102)))
    }

    @Test
    fun checkSendInUntil2() {
        /* This test sends 2 messages to the target, inside an until with 2 iterations.
           Explanation is identical to test "checkSendInRepeat2"
         */
        checkFailedSendInStatement(sendInUntilChecker2, listOf(listOf(102, 103), listOf(102, 102)))
    }

    @Test
    fun checkSendInUntil3() {
        /* This test sends 3 messages to the target, inside an until with 3 iterations.
           Explanation is identical to test "checkSendInRepeat3"
         */
        checkFailedSendInStatement(
            sendInUntilChecker3,
            listOf(
                listOf(102, 103, 103),
                listOf(102, 103, 102),
                listOf(102, 102, 103),
                listOf(102, 102, 102),
            ),
        )
    }

    @Test
    fun checkSendInUntil4() {
        // This test sends 4 messages to the target, inside an until with 4 iterations.
        // 4 iterations or more reaches the limit in which TSA unfolds until statements. As such, there shouldn't exist
        // runs generated by TSA.
        checkNoRunsSendInStatement(sendInUntilChecker4)
    }
}
