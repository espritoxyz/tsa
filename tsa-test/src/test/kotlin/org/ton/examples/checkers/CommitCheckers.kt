package org.ton.examples.checkers

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
import org.usvm.test.resolver.TvmSuccessfulExecution
import kotlin.io.path.readText
import kotlin.test.Ignore
import kotlin.test.Test

private const val EXIT_CODE = 256
private const val UNSUPPORTED_MESSAGE = "Verifying thrown exceptions inside checker contracts are not supported"

class CommitCheckers {
    private val targetContract = "/checkers/commit/target_contract.fc"
    private val companionContract = "/checkers/commit/companion_contract.fc"
    private val communicationSchemeJson = "/checkers/commit/commit-test-scheme.json"
    private val noCommitInC4Checker = "/checkers/commit/c4_no_commit.fc"
    private val oneCommitInC4Checker = "/checkers/commit/c4_one_commit.fc"
    private val oneCommitPlusUpdateInC4Checker = "/checkers/commit/c4_one_commit_plus_update_attempt.fc"
    private val twoCommitsInC4Checker = "/checkers/commit/c4_two_commits.fc"
    private val twoCommitsPlusUpdateInC4Checker = "/checkers/commit/c4_two_commits_plus_update_attempt.fc"
    private val noCommitInC5Checker = "/checkers/commit/c5_no_commit.fc"
    private val oneCommitInC5Checker = "/checkers/commit/c5_one_commit.fc"
    private val oneCommitPlusSendInC5Checker = "/checkers/commit/c5_one_commit_plus_send_attempt.fc"
    private val twoCommitsInC5Checker = "/checkers/commit/c5_two_commits.fc"
    private val twoCommitsPlusSendInC5Checker = "/checkers/commit/c5_two_commits_plus_send_attempt.fc"

    private fun runC4CommitTest(checkerPathStr: String) {
        val analyzedPath = extractResource(targetContract)
        val checkerPath = extractResource(checkerPathStr)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true
            )
        val analyzedContract = getFuncContract(analyzedPath, FIFT_STDLIB_RESOURCE)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID
            )

        checkInvariants(
            tests,
            listOf { test ->
                val result = test.result
                if (result is TvmMethodFailure) {
                    result.exitCode == EXIT_CODE
                } else {
                    result is TvmSuccessfulExecution
                }
            }
        )

        // There must exist at least one test that produced error code EXIT_CODE
        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == EXIT_CODE }
        )
    }

    private fun runC5CommitTest(checkerPathStr: String) {
        val senderPath = extractResource(targetContract)
        val recipientPath = extractResource(companionContract)
        val checkerPath = extractResource(checkerPathStr)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true
            )
        val analyzedSender = getFuncContract(senderPath, FIFT_STDLIB_RESOURCE)
        val analyzedRecipient = getFuncContract(recipientPath, FIFT_STDLIB_RESOURCE)

        val communicationSchemePath = extractResource(communicationSchemeJson)
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
                listOf(checkerContract, analyzedSender, analyzedRecipient),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options
            )

        checkInvariants(
            tests,
            listOf { test ->
                val result = test.result
                if (result is TvmMethodFailure) {
                    result.exitCode == EXIT_CODE
                } else {
                    result is TvmSuccessfulExecution
                }
            }
        )

        // There must exist at least one test that produced error code EXIT_CODE
        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode == EXIT_CODE }
        )
    }

    @Ignore(UNSUPPORTED_MESSAGE)
    @Test
    fun noCommitToC4Test() {
        runC4CommitTest(noCommitInC4Checker)
    }

    @Ignore(UNSUPPORTED_MESSAGE)
    @Test
    fun oneCommitToC4Test() {
        runC4CommitTest(oneCommitInC4Checker)
    }

    @Ignore(UNSUPPORTED_MESSAGE)
    @Test
    fun oneCommitPlusUpdateAttemptToC4Test() {
        runC4CommitTest(oneCommitPlusUpdateInC4Checker)
    }

    @Ignore(UNSUPPORTED_MESSAGE)
    @Test
    fun twoCommitsToC4Test() {
        runC4CommitTest(twoCommitsInC4Checker)
    }

    @Ignore(UNSUPPORTED_MESSAGE)
    @Test
    fun twoCommitsPlusUpdateAttemptToC4Test() {
        runC4CommitTest(twoCommitsPlusUpdateInC4Checker)
    }

    @Ignore(UNSUPPORTED_MESSAGE)
    @Test
    fun noCommitToC5Test() {
        runC5CommitTest(noCommitInC5Checker)
    }

    @Ignore(UNSUPPORTED_MESSAGE)
    @Test
    fun oneCommitToC5Test() {
        runC5CommitTest(oneCommitInC5Checker)
    }

    @Ignore(UNSUPPORTED_MESSAGE)
    @Test
    fun oneCommitPlusSendAttemptToC5Test() {
        runC5CommitTest(oneCommitPlusSendInC5Checker)
    }

    @Ignore(UNSUPPORTED_MESSAGE)
    @Test
    fun twoCommitsToC5Test() {
        runC5CommitTest(twoCommitsInC5Checker)
    }

    @Ignore(UNSUPPORTED_MESSAGE)
    @Test
    fun twoCommitsPlusSendAttemptToC5Test() {
        runC5CommitTest(twoCommitsPlusSendInC5Checker)
    }
}
