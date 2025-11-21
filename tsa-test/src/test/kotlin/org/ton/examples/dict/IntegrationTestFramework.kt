package org.ton.examples.dict

import org.junit.jupiter.api.DynamicTest
import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.utils.TvmTestExecutor
import org.ton.test.utils.executionCode
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeSpecificMethod
import org.usvm.logger
import org.usvm.machine.TvmOptions
import org.usvm.machine.toMethodId
import java.io.File

/**
 * By convention for this kind of tests, we will assume that error codes of 3** represent an assumption
 * (and we expect to find them in some test), 4** represent an assertion (and we expect to
 * NOT find them in any test) and 5** represent a possible successful execution
 * (and we expect to find them in some test)
 */

fun runTestsInFileDefault(
    file: File,
    testName: String,
    options: TvmOptions = TvmOptions(),
    generateTests: Boolean = true,
) {
    logger.info("Executing test \"$testName\"")
    logger.info("File is ${file.absolutePath}")
    val content = file.readText()
    val throwRegex = Regex("""\b(?:throw|throw_unless|throw_if)\(\s*(?<code>\d{3})""")
    val foundCodes =
        throwRegex
            .findAll(content)
            .mapNotNull {
                val isCommented = existsFuncCommentBeforeOnTheLine(it.range.first, content)
                if (isCommented) {
                    println("${it.groups["code"]!!.value} is commented")
                    null
                } else {
                    it.groups["code"]!!.value.toInt()
                }
            }.toList()
    val assumes = foundCodes.filter { it in 300..399 }
    val asserts = foundCodes.filter { it in 400..499 }
    val success = foundCodes.filter { it in 500..599 }
    logger.info("Assumption error codes: $assumes")
    logger.info("Assertion error codes: $asserts")
    logger.info("Successful execution error codes: $success")
    val tests = funcCompileAndAnalyzeSpecificMethod(file.toPath(), methodId = 0.toMethodId(), tvmOptions = options)
    val exitCodes = tests.map { it.executionCode() }
    logger.info("Found exit codes: $exitCodes")
    val foundAssertsViolation = exitCodes.intersect(asserts)
    if (foundAssertsViolation.isNotEmpty()) {
        error("Found assert violation: $foundAssertsViolation")
    }
    val expected = assumes + success
    val expectedNotFound = expected.subtract(exitCodes)
    if (expectedNotFound.isNotEmpty()) {
        error("Expected but not found exit codes: $expectedNotFound")
    }
    logger.info("Finished symbolic execution of test ${file.name}")
    if (generateTests) {
        logger.info("Beginning the execution of generated tests for ${file.name}")
        TvmTestExecutor.executeGeneratedTests(tests, file.toPath(), TsRenderer.ContractType.Func)
        logger.info("Ending the execution of generated tests for ${file.name}")
    }
}

private fun existsFuncCommentBeforeOnTheLine(
    i: Int,
    content: String,
): Boolean {
    var i1 = i
    var isCommented = false
    while (i1 >= 0 && content[i1] != '\n') {
        if (content[i1] == ';' && i1 - 1 >= 0 && content[i1 - 1] != ';') {
            isCommented = true
            break
        }
        i1--
    }
    return isCommented
}

/**
 * For the semantics of the integration test, see [runTestsInFileDefault].
 *
 * Beware --- if the resulted directory is empty, the list returned by this function will be empty
 * and Gradle will not see the required tests and report an error.
 */
fun runTestsInDirectory(
    resourceDirectory: String,
    pattern: String = ".*",
    options: TvmOptions = TvmOptions(),
    generateTests: Boolean = true,
): List<DynamicTest> {
    logger.info("Executing tests in $resourceDirectory that match pattern:\n    $pattern")
    val basePath = extractResource(resourceDirectory)

    val files =
        basePath
            .toFile()
            .walk()
            .filter { it.isFile && it.name.endsWith(".fc") && !it.name.contains("util") }
            .toList()
            .filter { file ->
                file.toRelativeString(basePath.toFile()).matches(pattern.toRegex())
            }
    if (files.isEmpty()) {
        throw IllegalArgumentException("No test files found")
    }
    return files
        .map { file ->
            val name = file.toRelativeString(basePath.toFile())
            DynamicTest.dynamicTest(name, file.toURI()) { runTestsInFileDefault(file, name, options, generateTests) }
        }
}
