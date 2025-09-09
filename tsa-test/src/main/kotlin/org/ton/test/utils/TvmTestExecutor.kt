package org.ton.test.utils

import mu.KLogging
import org.ton.test.gen.TestStatus
import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.gen.executeTests
import org.ton.test.gen.generateTests
import org.usvm.test.resolver.TvmContractSymbolicTestResult
import org.usvm.test.resolver.TvmSymbolicTestSuite
import org.usvm.utils.executeCommandWithTimeout
import java.nio.file.Path
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

object TvmTestExecutor {
    private const val SANDBOX_PROJECT_PATH: String = "/sandbox"

    private val PROJECT_INIT_TIMEOUT = 5.minutes
    private val TEST_EXECUTION_TIMEOUT = 5.minutes

    private val logger = object : KLogging() {}.logger

    private fun executeTests(
        project: Path,
        generatedTestsPath: String,
    ) {
        val (testResults, successful) =
            executeTests(
                projectPath = project,
                testFileName = generatedTestsPath,
                testsExecutionTimeout = TEST_EXECUTION_TIMEOUT
            )
        val allTests = testResults.flatMap { it.assertionResults }
        val failedTests = allTests.filter { it.status == TestStatus.FAILED }

        val failMessage = "${failedTests.size} of ${allTests.size} generated tests failed: ${failedTests.joinToString {
            it.fullName
        }}"

        assertTrue(successful, failMessage)

        logger.info("Generated tests executed")
    }

    val project: Path
        get() = extractResource(SANDBOX_PROJECT_PATH)

    private fun executeGeneratedTests(generateTestsBlock: (Path) -> String?) {
        val generatedTests =
            generateTestsBlock(project)
                ?: return
        executeTests(project, generatedTests)
    }

    fun executeGeneratedTests(
        testResult: TvmContractSymbolicTestResult,
        sources: Path,
        contractType: TsRenderer.ContractType,
        useMinimization: Boolean = false,
    ) {
        executeGeneratedTests { project ->
            generateTests(
                testResult,
                project,
                sources.toAbsolutePath(),
                contractType,
                useMinimization = useMinimization
            )
        }
    }

    fun executeGeneratedTests(
        testSuite: TvmSymbolicTestSuite,
        sources: Path,
        contractType: TsRenderer.ContractType,
        useMinimization: Boolean = false,
    ) {
        executeGeneratedTests(TvmContractSymbolicTestResult(listOf(testSuite)), sources, contractType, useMinimization)
    }

    init {
        val project = extractResource(SANDBOX_PROJECT_PATH).toFile()
        val (exitCode, _, _, _) =
            executeCommandWithTimeout(
                command = "npm i",
                timeoutSeconds = PROJECT_INIT_TIMEOUT.inWholeSeconds,
                processWorkingDirectory = project
            )

        check(exitCode == 0) {
            "Couldn't initialize the test sandbox project"
        }
    }
}
