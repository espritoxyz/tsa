package org.ton.examples.types.integrative

import org.ton.examples.checkInvariants
import org.ton.examples.extractTlbInfo
import org.ton.examples.funcCompileAndAnalyzeAllMethods
import org.ton.examples.propertiesFound
import org.usvm.machine.types.TvmReadingOfUnexpectedType
import org.usvm.test.resolver.TvmExecutionWithStructuralError
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntSwitchTest {
    private val typesPath = "/demo-tlb/int_switch/types.json"
    private val correctPath = "/demo-tlb/int_switch/int_switch_correct.fc"
    private val wrongPath = "/demo-tlb/int_switch/int_switch_wrong.fc"

    @Test
    fun testCorrect() {
        val path = this::class.java.getResource(correctPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource bytecode $correctPath")
        val inputInfo = extractTlbInfo(typesPath)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        assertTrue(tests.any { it.result is TvmSuccessfulExecution })

        checkInvariants(
            tests,
            listOf { test ->
                test.result !is TvmExecutionWithStructuralError && test.result !is TvmMethodFailure
            }
        )
    }

    @Test
    fun testWrong() {
        val path = this::class.java.getResource(wrongPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource bytecode $wrongPath")
        val inputInfo = extractTlbInfo(typesPath)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        propertiesFound(
            tests,
            listOf { test ->
                val result = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                result.exit is TvmReadingOfUnexpectedType
            }
        )
    }
}
