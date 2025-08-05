package org.ton.examples.minimization

import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.usvm.machine.toMethodId
import org.usvm.test.minimization.minimizeTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MinimizationTest {
    private val contractPath: String = "/minimization/minimization.fc"

    @Test
    fun testMinimization() {
        val funcResourcePath = extractResource(contractPath)

        val methodStates = funcCompileAndAnalyzeAllMethods(
            funcResourcePath,
            methodWhiteList = setOf(0.toMethodId())
        )

        val initialTest = methodStates.single().tests
        val minimizedTests1 = minimizeTestCase(initialTest, branchInstructionsNumber = 0)
        val minimizedTests2 = minimizeTestCase(initialTest, branchInstructionsNumber = 2)

        assertTrue(initialTest.size > minimizedTests1.size)
        assertEquals(initialTest.size, minimizedTests2.size)
    }
}
