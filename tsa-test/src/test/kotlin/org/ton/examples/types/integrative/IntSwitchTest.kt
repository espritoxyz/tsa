package org.ton.examples.types.integrative

import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.extractTlbInfo
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.usvm.machine.types.TvmReadingOfUnexpectedType
import org.usvm.test.resolver.TvmExecutionWithStructuralError
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntSwitchTest {
    private val typesPath = "/demo-tlb/int_switch/types.json"
    private val correctPath = "/demo-tlb/int_switch/int_switch_correct.fc"
    private val wrongPath = "/demo-tlb/int_switch/int_switch_wrong.fc"

    @Test
    fun testCorrect() {
        val path = extractResource(correctPath)
        val inputInfo = extractTlbInfo(typesPath, this::class)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        assertTrue(tests.any { it.result is TvmSuccessfulExecution })

        checkInvariants(
            tests,
            listOf { test ->
                test.result !is TvmExecutionWithStructuralError && test.result !is TvmTestFailure
            },
        )
    }

    @Test
    fun testWrong() {
        val path = extractResource(wrongPath)
        val inputInfo = extractTlbInfo(typesPath, this::class)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmTestFailure })

        propertiesFound(
            tests,
            listOf { test ->
                val result = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                result.exit is TvmReadingOfUnexpectedType
            },
        )
    }
}
