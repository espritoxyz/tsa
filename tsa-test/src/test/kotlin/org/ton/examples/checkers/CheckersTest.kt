package org.ton.examples.checkers

import org.ton.bitstring.BitString
import org.ton.cell.Cell
import org.ton.communicationSchemeFromJson
import org.ton.test.utils.FIFT_STDLIB_RESOURCE
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import kotlin.io.path.readText
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class CheckersTest {
    private val internalCallChecker = "/checkers/send_internal.fc"
    private val internalCallCheckerWithCapture = "/checkers/send_internal_with_capture.fc"
    private val balancePath = "/args/balance.fc"
    private val bounceCheckerPath = "/checkers/bounce.fc"
    private val getC4CheckerPath = "/checkers/get_c4.fc"
    private val emptyContractPath = "/empty_contract.fc"
    private val senderBouncePath = "/args/send_bounce_true.fc"
    private val recepientBouncePath = "/args/receive_bounce_msg.fc"
    private val bounceTestScheme = "/checkers/bounce-test-scheme.json"

    @Test
    fun testConsistentBalanceThroughChecker() {
        runTestConsistentBalanceThroughChecker(internalCallChecker)
    }

    @Test
    fun testConsistentBalanceThroughCheckerWithCapture() {
        runTestConsistentBalanceThroughChecker(internalCallCheckerWithCapture)
    }

    private fun runTestConsistentBalanceThroughChecker(checkerPathStr: String) {
        val path = extractResource(balancePath)
        val checkerPath = extractResource(checkerPathStr)

        val checkerContract = getFuncContract(
            checkerPath,
            FIFT_STDLIB_RESOURCE,
            isTSAChecker = true
        )
        val analyzedContract = getFuncContract(path, FIFT_STDLIB_RESOURCE)

        val tests = analyzeInterContract(
            listOf(checkerContract, analyzedContract),
            startContractId = 0,
            methodId = TvmContext.RECEIVE_INTERNAL_ID,
        )

        propertiesFound(
            tests,
            listOf(
                { test -> test.result is TvmSuccessfulExecution },
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1001 },
            )
        )

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode != 1000 },
        )
    }

    @Test
    fun testGetC4() {
        val path = extractResource(emptyContractPath)
        val checkerPath = extractResource(getC4CheckerPath)

        val checkerContract = getFuncContract(
            checkerPath,
            FIFT_STDLIB_RESOURCE,
            isTSAChecker = true
        )
        val analyzedContract = getFuncContract(path, FIFT_STDLIB_RESOURCE)

        val tests = analyzeInterContract(
            listOf(checkerContract, analyzedContract),
            startContractId = 0,
            methodId = TvmContext.RECEIVE_INTERNAL_ID,
            concreteContractData = listOf(
                TvmConcreteContractData(),
                TvmConcreteContractData(contractC4 = Cell(BitString.of("00000100"))),
            )
        )

        assertTrue { tests.isNotEmpty() }

        checkInvariants(
            tests,
            listOf { test -> test.result is TvmSuccessfulExecution }
        )
    }

    @Ignore("Bounced messages in intercontracts communication are not supported")
    @Test
    fun bounceTest() {
        val pathSender = extractResource(senderBouncePath)
        val pathRecepient = extractResource(recepientBouncePath)
        val checkerPath = extractResource(bounceCheckerPath)

        val checkerContract = getFuncContract(
            checkerPath,
            FIFT_STDLIB_RESOURCE,
            isTSAChecker = true
        )
        val analyzedSender = getFuncContract(pathSender, FIFT_STDLIB_RESOURCE)
        val analyzedRecepient = getFuncContract(pathRecepient, FIFT_STDLIB_RESOURCE)

        val communicationSchemePath = extractResource(bounceTestScheme)
        val communicationScheme = communicationSchemeFromJson(communicationSchemePath.readText())

        val options = TvmOptions(
            intercontractOptions = IntercontractOptions(
                communicationScheme = communicationScheme,
            ),
            enableOutMessageAnalysis = true,
        )

        val tests = analyzeInterContract(
            listOf(checkerContract, analyzedSender, analyzedRecepient),
            startContractId = 0,
            methodId = TvmContext.RECEIVE_INTERNAL_ID,
            options = options,
            concreteContractData = listOf(
                TvmConcreteContractData(),
                TvmConcreteContractData(contractC4 = Cell(BitString.of("0"))),
                TvmConcreteContractData(),
            )
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
}
