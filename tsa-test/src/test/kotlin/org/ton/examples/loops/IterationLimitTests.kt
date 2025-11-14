package org.ton.examples.loops

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.ton.examples.dict.runTestsInDirectory
import org.ton.examples.dict.runTestsInFileDefault
import org.ton.test.utils.extractResource
import org.usvm.machine.TvmOptions

class IterationLimitTests {
    private val allTests = "/loops/iter-limits"

    @TestFactory
    fun `all iter-limits tests`(): List<DynamicTest> =
        runTestsInDirectory(
            allTests,
            System.getenv("ITERLIMITS_TESTS") ?: ".*",
            options = TvmOptions(loopIterationLimit = 2),
            generateTests = false,
        )

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
                runTestsInFileDefault(
                    extractResource(test).toFile(),
                    testName,
                    options = TvmOptions(loopIterationLimit = 2),
                    generateTests = false,
                )
            }
        }
}
