package org.ton.examples.checkers

import org.junit.jupiter.api.Tag
import org.ton.bytecode.TsaContractCode
import org.ton.test.utils.assertInvariantsHold
import org.ton.test.utils.assertNotEmpty
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.doesNotEndWithExitCode
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractCommunicationSchemeFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.hasExitCode
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.test.resolver.TvmSymbolicTestSuite
import kotlin.test.Test

@Tag("intercontract")
class BounceCheckerTest {
    private object BounceChecker {
        const val BOUNCE_CHECKER = "/checkers/bounce-checker/bounce-checker.fc"
        const val SCHEME = "/checkers/bounce-checker/bounce-checker-scheme.json"
        const val NAIVE_SENDER = "/checkers/bounce-checker/naive-sender.fc"
        const val NAIVE_SENDER_WITH_CHILDREN = "/checkers/bounce-checker/naive-sender-with-4-children.fc"
        const val NOT_NAIVE_SENDER = "/checkers/bounce-checker/not-naive-sender.fc"
        const val THROWER = "/checkers/bounce-checker/always-thrower.fc"
        const val BAD_BOUNCED_HANDLER = "/checkers/bounce-checker/sender-with-bad-bounced-handling.fc"

        const val FAILURE_DURING_BOUNCE_HANDLING_EXIT_CODE = 1000
        const val SENT_MSG_ON_BOUNCE_EXIT_CODE = 1001
    }

    @Test
    fun `bounce naive sender`() {
        val naiveSender = extractFuncContractFromResource(BounceChecker.NAIVE_SENDER)
        val tests = bounceCheckerBase(naiveSender)
        tests.assertPropertiesFound(
            hasExitCode(BounceChecker.SENT_MSG_ON_BOUNCE_EXIT_CODE),
        )
    }

    @Test
    fun `bounce naive sender with a body with many children`() {
        val naiveSender = extractFuncContractFromResource(BounceChecker.NAIVE_SENDER_WITH_CHILDREN)
        val tests = bounceCheckerBase(naiveSender)
        tests.assertPropertiesFound(
            hasExitCode(BounceChecker.SENT_MSG_ON_BOUNCE_EXIT_CODE),
        )
    }

    @Test
    fun `bounce non-naive sender`() {
        val nonNaiveSender = extractFuncContractFromResource(BounceChecker.NOT_NAIVE_SENDER)
        val tests = bounceCheckerBase(nonNaiveSender)
        tests.assertNotEmpty()
        tests.assertInvariantsHold(
            doesNotEndWithExitCode(BounceChecker.SENT_MSG_ON_BOUNCE_EXIT_CODE),
            doesNotEndWithExitCode(BounceChecker.FAILURE_DURING_BOUNCE_HANDLING_EXIT_CODE),
        )
    }

    @Test
    fun `bounce with bad handler`() {
        val badHandler = extractFuncContractFromResource(BounceChecker.BAD_BOUNCED_HANDLER)
        val tests = bounceCheckerBase(badHandler)
        tests.assertPropertiesFound(
            hasExitCode(BounceChecker.FAILURE_DURING_BOUNCE_HANDLING_EXIT_CODE),
        )
    }

    private fun bounceCheckerBase(naiveSender: TsaContractCode): TvmSymbolicTestSuite {
        val bounceChecker = extractCheckerContractFromResource(BounceChecker.BOUNCE_CHECKER)
        val thrower = extractFuncContractFromResource(BounceChecker.THROWER)
        val scheme = extractCommunicationSchemeFromResource(BounceChecker.SCHEME)
        val options =
            TvmOptions(
                turnOnTLBParsingChecks = false,
                enableOutMessageAnalysis = true,
                stopOnFirstError = false,
                intercontractOptions = IntercontractOptions(communicationScheme = scheme),
            )
        val tests =
            analyzeInterContract(
                listOf(bounceChecker, naiveSender, thrower),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )
        return tests
    }
}
