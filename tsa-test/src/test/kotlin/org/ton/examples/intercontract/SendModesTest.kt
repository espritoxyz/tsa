package org.ton.examples.intercontract

import org.ton.communicationSchemeFromJson
import org.ton.test.utils.FIFT_STDLIB_RESOURCE
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.test.resolver.TvmMethodFailure
import kotlin.io.path.readText
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class SendModesTest {
    private val remainingBalanceContract = "/intercontract/modes/send_remaining_balance.fc"
    private val remainingBalanceChecker = "/intercontract/modes/remaining_balance.fc"
    private val remainingValueContract = "/intercontract/modes/send_remaining_value.fc"
    private val remainingValueChecker = "/intercontract/modes/remaining_value.fc"
    private val ignoreErrorsContract = "/intercontract/modes/send_ignore_error_flag.fc"
    private val ignoreErrorsChecker = "/intercontract/modes/ignore_error.fc"
    private val ignoreErrorTestScheme = "/intercontract/modes/ignore_error_test_scheme.json"
    private val recipientBouncePath = "/args/receive_bounce_msg.fc"

    @Ignore("SendRemainingBalance mode is not supported")
    @Test
    fun sendRemainingBalanceTest() {
        val pathContract = extractResource(remainingBalanceContract)
        val checkerPath = extractResource(remainingBalanceChecker)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true,
            )
        val analyzedContract = getFuncContract(pathContract, FIFT_STDLIB_RESOURCE)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
            )

        assertTrue { tests.isNotEmpty() }

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 257 },
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
                isTSAChecker = true,
            )
        val analyzedContract = getFuncContract(pathContract, FIFT_STDLIB_RESOURCE)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
            )

        assertTrue { tests.isNotEmpty() }

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == 257 },
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
                isTSAChecker = true,
            )
        val analyzedSender = getFuncContract(pathSender, FIFT_STDLIB_RESOURCE)
        val analyzedRecepient = getFuncContract(pathRecepient, FIFT_STDLIB_RESOURCE)

        val communicationSchemePath = extractResource(ignoreErrorTestScheme)
        val communicationScheme = communicationSchemeFromJson(communicationSchemePath.readText())

        val options =
            TvmOptions(
                intercontractOptions =
                IntercontractOptions(
                    communicationScheme = communicationScheme,
                ),
                enableOutMessageAnalysis = true,
            )

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedSender, analyzedRecepient),
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
