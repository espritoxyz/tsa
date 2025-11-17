package org.ton.examples.dict

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.ton.test.utils.extractResource

class InputDictIntegrationTest {
    private val allTests = "/dict/input-dict"

    @TestFactory
    fun `all dictionary tests`(): List<DynamicTest> =
        runTestsInDirectory(allTests, System.getenv("INPUTDICT_TESTS") ?: ".*")

    @Disabled
    @TestFactory
    fun runSingleTest() =
        List(
            10,
        ) { number ->
            DynamicTest.dynamicTest("runSingleTest $number") {
                val baseFolder = "/dict/input-dict/"
                val testName = "complex/filter-256.fc"
                val test = baseFolder + testName
                runTestsInFileDefault(extractResource(test).toFile(), testName)
            }
        }
}
