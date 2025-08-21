package org.ton.examples.checkers

import org.ton.bitstring.BitString
import org.ton.cell.Cell
import org.ton.examples.args.ArgsConstraintsTest
import org.ton.test.utils.FIFT_STDLIB_RESOURCE
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.machine.getResourcePath
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import kotlin.test.Test
import kotlin.test.assertTrue

class CheckersTest {
    private val internalCallChecker = "/checkers/send_internal.fc"
    private val internalCallCheckerWithCapture = "/checkers/send_internal_with_capture.fc"
    private val balancePath = "/args/balance.fc"
    private val getC4CheckerPath = "/checkers/get_c4.fc"
    private val emptyContractPath = "/empty_contract.fc"

    @Test
    fun testConsistentBalanceThroughChecker() {
        runTestConsistentBalanceThroughChecker(internalCallChecker)
    }

    @Test
    fun testConsistentBalanceThroughCheckerWithCapture() {
        runTestConsistentBalanceThroughChecker(internalCallCheckerWithCapture)
    }

    private fun runTestConsistentBalanceThroughChecker(checkerPathStr: String) {
        val path = getResourcePath<ArgsConstraintsTest>(balancePath)
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
        val path = getResourcePath<ArgsConstraintsTest>(emptyContractPath)
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
}
