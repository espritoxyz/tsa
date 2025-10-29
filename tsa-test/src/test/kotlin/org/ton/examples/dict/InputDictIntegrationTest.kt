package org.ton.examples.dict

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.ton.test.utils.extractResource

class InputDictIntegrationTest {
    private val allTests = "/dict/input-dict"
    private val setDeleteTests = "/dict/input-dict/set-delete"
    private val queryTests = "/dict/input-dict/queries"
    private val minMaxDelete = "/dict/input-dict/min-max-delete"

    @TestFactory
    fun `all dictionary tests`(): List<DynamicTest> = runTestsInDirectory(allTests)

    @Disabled
    @TestFactory
    fun `query tests`(): List<DynamicTest> = runTestsInDirectory(queryTests)

    @Disabled
    @TestFactory
    fun `set-delete tests`(): List<DynamicTest> = runTestsInDirectory(setDeleteTests)

    @Disabled
    @TestFactory
    fun `min-max-delete tests`(): List<DynamicTest> = runTestsInDirectory(minMaxDelete)

    //    @Disabled
    @TestFactory
    fun runSingleTest() =
        DynamicTest.dynamicTest("runSingleTest") {
            val baseFolder = "/dict/input-dict/"
            val testName = "queries/get-max-returns-max-reordered.fc"
            val test = baseFolder + testName
            runTestsInFileDefault(extractResource(test).toFile(), testName)
        }
}
