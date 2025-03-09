package org.usvm.utils

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ProcessExecutionResult(
    val exitValue: Int,
    val completedInTime: Boolean,
    val output: List<String>,
    val errors: List<String>,
)

fun executeCommandWithTimeout(
    command: String,
    timeoutSeconds: Long,
    processWorkingDirectory: File? = null,
    additionalEnvironment: Map<String, String> = emptyMap(),
): ProcessExecutionResult = executeCommandWithTimeout(
    command.split(" "),
    timeoutSeconds,
    processWorkingDirectory,
    additionalEnvironment
)

fun executeCommandWithTimeout(
    command: List<String>,
    timeoutSeconds: Long,
    processWorkingDirectory: File? = null,
    additionalEnvironment: Map<String, String> = emptyMap(),
): ProcessExecutionResult {
    val processBuilder = ProcessBuilder(command)
        .directory(processWorkingDirectory)
    processBuilder.environment().putAll(additionalEnvironment)

    val process = processBuilder.start()

    // Use an executor to read output and error streams concurrently
    val executor = Executors.newFixedThreadPool(2)
    val output = mutableListOf<String>()
    val errors = mutableListOf<String>()

    val outputTask = executor.submit {
        process.inputStream.bufferedReader().useLines { lines ->
            output.addAll(lines)
        }
    }

    val errorTask = executor.submit {
        process.errorStream.bufferedReader().useLines { lines ->
            errors.addAll(lines)
        }
    }

    // Wait for the process to complete within the timeout
    val completedInTime = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

    if (!completedInTime) {
        process.destroyForcibly() // Kill the process if it exceeds the timeout
        executor.shutdownNow() // Interrupt reading threads
    }

    // Wait for stream-reading threads to finish if they were not interrupted
    outputTask.get() // Ensure output stream is fully read
    errorTask.get() // Ensure error stream is fully read

    executor.shutdown()

    return ProcessExecutionResult(process.exitValue(), completedInTime, output, errors) // Return both outputs
}
