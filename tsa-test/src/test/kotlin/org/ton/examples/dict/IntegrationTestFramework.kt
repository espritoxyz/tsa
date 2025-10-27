package org.ton.examples.dict

import org.junit.jupiter.api.DynamicTest
import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.utils.TvmTestExecutor
import org.ton.test.utils.executionCode
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeSpecificMethod
import org.usvm.logger
import org.usvm.machine.toMethodId
import java.io.File

fun defaultTest(file: File) {
    logger.info("Executing test ${file.name}")
    val content = file.readText()
    val assumes = Regex("3\\d\\d").findAll(content).map { it.groupValues[0].toInt() }.toList()
    val asserts = Regex("4\\d\\d").findAll(content).map { it.groupValues[0].toInt() }.toList()
    val success = Regex("5\\d\\d").findAll(content).map { it.groupValues[0].toInt() }.toList()
    val tests = funcCompileAndAnalyzeSpecificMethod(file.toPath(), methodId = 1.toMethodId())
    val exitCodes = tests.map { it.executionCode() }
    logger.info("Found exit codes: $exitCodes")
    for (exitCode in assumes + success) {
        if (exitCode !in exitCodes) {
            error("Did not find exit code $exitCode")
        }
    }
    for (assert in asserts) {
        if (assert in exitCodes) {
            error("Found exit code $assert")
        }
    }
    logger.info("Finished symbolic execution of test ${file.name}")
    logger.info("Beginning the execution of generated tests for ${file.name}")
    TvmTestExecutor.executeGeneratedTests(tests, file.toPath(), TsRenderer.ContractType.Func)
    logger.info("Ending the execution of generated tests for ${file.name}")
}

fun runTestsInDirectory(resourceDirectory: String): List<DynamicTest> {
    val basePath = extractResource(resourceDirectory)

    val files =
        basePath
            .toFile()
            .walk()
            .filter { it.isFile && it.name.endsWith(".fc") && !it.name.contains("util") }
            .toList()
    return files
        .map { file ->
            val name = file.toRelativeString(basePath.toFile())
            DynamicTest.dynamicTest(name) { defaultTest(file) }
        }
}
