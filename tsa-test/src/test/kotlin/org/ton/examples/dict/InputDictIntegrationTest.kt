package org.ton.examples.dict

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class InputDictIntegrationTest {
    private val allTests = "/dict/input-dict"
    private val setDeleteTests = "/dict/input-dict/set-delete"

    @TestFactory
    fun unitTests(): List<DynamicTest> = runTestsInDirectory(allTests)

    @Disabled
    @TestFactory
    fun `set-delete tests`(): List<DynamicTest> = runTestsInDirectory(setDeleteTests)
}
