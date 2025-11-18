package org.ton.examples.checkers

import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractCommunicationSchemeFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.state.InsufficientFunds
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Test

private const val EXIT_CODE = 256

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
        val checkerContract = extractCheckerContractFromResource(checkerPathStr)
        val analyzedContract = extractFuncContractFromResource(targetContract)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedContract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = TvmOptions(stopOnFirstError = false),
            )

        checkInvariants(
            tests,
            listOf { test ->
                val result = test.result
                if (result is TvmTestFailure) {
                    result.exitCode == EXIT_CODE
                } else {
                    result is TvmSuccessfulExecution
                }
            },
        )

        // There must exist at least one test that produced error code EXIT_CODE
        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode == EXIT_CODE },
        )
    }

    private fun runC5CommitTest(checkerPathStr: String) {
        val checkerContract = extractCheckerContractFromResource(checkerPathStr)
        val analyzedSender = extractFuncContractFromResource(targetContract)
        val analyzedRecipient = extractFuncContractFromResource(companionContract)
        val communicationScheme = extractCommunicationSchemeFromResource(communicationSchemeJson)

        val options =
            TvmOptions(
                intercontractOptions =
                    IntercontractOptions(
                        communicationScheme = communicationScheme,
                    ),
                enableOutMessageAnalysis = true,
                stopOnFirstError = false,
            )

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedSender, analyzedRecipient),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        checkInvariants(
            tests,
            listOf { test ->
                val result = test.result
                if (result is TvmTestFailure) {
                    result.exitCode == EXIT_CODE || result.failure.exit is InsufficientFunds
                } else {
                    result is TvmSuccessfulExecution
                }
            },
        )

        // There must exist at least one test that produced error code EXIT_CODE
        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode == EXIT_CODE },
        )
    }

    @Test
    fun noCommitToC4Test() {
        runC4CommitTest(noCommitInC4Checker)
    }

    @Test
    fun oneCommitToC4Test() {
        runC4CommitTest(oneCommitInC4Checker)
    }

    @Test
    fun oneCommitPlusUpdateAttemptToC4Test() {
        runC4CommitTest(oneCommitPlusUpdateInC4Checker)
    }

    @Test
    fun twoCommitsToC4Test() {
        runC4CommitTest(twoCommitsInC4Checker)
    }

    @Test
    fun twoCommitsPlusUpdateAttemptToC4Test() {
        runC4CommitTest(twoCommitsPlusUpdateInC4Checker)
    }

    @Test
    fun noCommitToC5Test() {
        runC5CommitTest(noCommitInC5Checker)
    }

    @Test
    fun oneCommitToC5Test() {
        runC5CommitTest(oneCommitInC5Checker)
    }

    @Test
    fun oneCommitPlusSendAttemptToC5Test() {
        runC5CommitTest(oneCommitPlusSendInC5Checker)
    }

    @Test
    fun twoCommitsToC5Test() {
        runC5CommitTest(twoCommitsInC5Checker)
    }

    @Test
    fun twoCommitsPlusSendAttemptToC5Test() {
        runC5CommitTest(twoCommitsPlusSendInC5Checker)
    }
}
