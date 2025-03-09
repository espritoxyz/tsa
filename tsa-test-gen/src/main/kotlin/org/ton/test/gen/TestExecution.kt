package org.ton.test.gen

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.ton.test.gen.dsl.render.TsRenderer
import org.usvm.utils.executeCommandWithTimeout
import org.usvm.utils.toText
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val TESTS_EXECUTION_DEFAULT_TIMEOUT = 40.seconds

@OptIn(ExperimentalSerializationApi::class)
fun executeTests(
    projectPath: Path,
    testFileName: String,
    testsExecutionTimeout: Duration = TESTS_EXECUTION_DEFAULT_TIMEOUT,
    testCommand: String = "yarn jest", // Could be also "jest" or "npm test"
): TestExecutionResult {
    val relativePath = Paths.get(TsRenderer.TESTS_DIR_NAME).resolve(testFileName)
    val command = "$testCommand --json $relativePath"

    val (exitValue, completedInTime, output, errors) = executeCommandWithTimeout(
        command,
        testsExecutionTimeout.inWholeSeconds,
        projectPath.toFile()
    )
    check(completedInTime) {
        "Tests execution has not finished in $testsExecutionTimeout"
    }

    // Jest exist with code 1 when any test fails https://github.com/jestjs/jest/issues/7803#issuecomment-460620632
    check(exitValue in 0..1) {
        "Tests execution finished with an error, exit code $exitValue, errors:\n${errors.toText()}"
    }

    val jsonOutput = output.first { it.first() == '{' && it.last() == '}' }
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    return json.decodeFromString(jsonOutput)
}


@Serializable
enum class TestStatus {
    @SerialName("passed")
    PASSED,
    @SerialName("failed")
    FAILED
}

@Serializable
data class MatcherResult(
    val pass: Boolean,
    val message: String,
)

@Serializable
data class FailureDetails(
    val error: String?,
    val matcherResult: MatcherResult?,
)

@Serializable
data class TestResult(
    val fullName: String,
    val status: TestStatus,
    val failureDetails: List<FailureDetails>?,
)

@Serializable
data class TestSuite(
    val assertionResults: List<TestResult>
)

@Serializable
data class TestExecutionResult(
    val testResults: List<TestSuite>,
    val success: Boolean
)
