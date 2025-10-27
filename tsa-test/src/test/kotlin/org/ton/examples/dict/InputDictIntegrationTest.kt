package org.ton.examples.dict

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.ton.test.utils.extractResource

class InputDictIntegrationTest {
    private val allTests = "/dict/input-dict"
    private val setDeleteTests = "/dict/input-dict/set-delete"

    @TestFactory
    fun `all dictionary tests`(): List<DynamicTest> = runTestsInDirectory(allTests)

    @Disabled
    @TestFactory
    fun `set-delete tests`(): List<DynamicTest> = runTestsInDirectory(setDeleteTests)

    @TestFactory
    fun runSingleTest() =
        DynamicTest.dynamicTest("runSingleTest") {
            val test = "/dict/input-dict/set-delete/delete-does-not-override-other-keys.fc"
            defaultTest(extractResource(test).toFile())
        }
}
