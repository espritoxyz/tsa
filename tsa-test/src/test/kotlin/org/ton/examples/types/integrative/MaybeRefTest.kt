package org.ton.examples.types.integrative

import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.extractTlbInfo
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.usvm.machine.types.TvmUnexpectedEndOfReading
import org.usvm.machine.types.TvmUnexpectedRefReading
import org.usvm.test.resolver.TvmExecutionWithStructuralError
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaybeRefTest {
    private val typesPath = "/demo-tlb/maybe_ref/types.json"
    private val correct1Path = "/demo-tlb/maybe_ref/correct_1.fc"
    private val correct2Path = "/demo-tlb/maybe_ref/correct_2.fc"
    private val loadWithoutMaybePath = "/demo-tlb/maybe_ref/load_without_maybe.fc"
    private val prematureEndParse = "/demo-tlb/maybe_ref/premature_end_parse.fc"

    @Test
    fun testCorrect1() {
        val path = extractResource(correct1Path)
        val inputInfo = extractTlbInfo(typesPath, this::class)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        assertTrue(tests.any { it.result is TvmSuccessfulExecution })

        checkInvariants(
            tests,
            listOf { test ->
                test.result !is TvmExecutionWithStructuralError && test.result !is TvmMethodFailure
            },
        )
    }

    @Test
    fun testCorrect2() {
        val path = extractResource(correct2Path)
        val inputInfo = extractTlbInfo(typesPath, this::class)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        assertTrue(tests.any { it.result is TvmSuccessfulExecution })

        checkInvariants(
            tests,
            listOf { test ->
                test.result !is TvmExecutionWithStructuralError && test.result !is TvmMethodFailure
            },
        )
    }

    @Test
    fun testLoadWithoutMaybe() {
        val path = extractResource(loadWithoutMaybePath)
        val inputInfo = extractTlbInfo(typesPath, this::class)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        propertiesFound(
            tests,
            listOf { test ->
                val result = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                result.exit is TvmUnexpectedRefReading
            },
        )
    }

    @Test
    fun testPrematureEndParse() {
        val path = extractResource(prematureEndParse)
        val inputInfo = extractTlbInfo(typesPath, this::class)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        propertiesFound(
            tests,
            listOf { test ->
                val result = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                result.exit is TvmUnexpectedEndOfReading
            },
        )
    }
}
