package org.ton.examples.types.integrative

import org.ton.examples.extractTlbInfo
import org.ton.examples.funcCompileAndAnalyzeAllMethods
import org.ton.examples.propertiesFound
import org.usvm.machine.types.TvmReadingOutOfSwitchBounds
import org.usvm.test.resolver.TvmExecutionWithStructuralError
import org.usvm.test.resolver.TvmSuccessfulExecution
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecursiveSchemeTest {
    private val typesPath = "/demo-tlb/recursive_scheme/types.json"
    private val correctPath = "/demo-tlb/recursive_scheme/correct.fc"
    private val noFlagRereading = "/demo-tlb/recursive_scheme/no_flag_rereading.fc"

    @Test
    fun testCorrect() {
        val path = this::class.java.getResource(correctPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource bytecode $correctPath")
        val inputInfo = extractTlbInfo(typesPath)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmExecutionWithStructuralError })
    }

    @Test
    fun testNoFlagRereading() {
        val path = this::class.java.getResource(noFlagRereading)?.path?.let { Path(it) }
            ?: error("Cannot find resource bytecode $noFlagRereading")
        val inputInfo = extractTlbInfo(typesPath)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        propertiesFound(
            tests,
            listOf { test ->
                val exit = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                exit.exit is TvmReadingOutOfSwitchBounds
            }
        )
    }
}
