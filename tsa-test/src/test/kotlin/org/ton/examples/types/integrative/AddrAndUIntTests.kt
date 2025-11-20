package org.ton.examples.types.integrative

import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.extractTlbInfo
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.usvm.machine.types.TvmReadingOfUnexpectedType
import org.usvm.machine.types.TvmUnexpectedEndOfReading
import org.usvm.machine.types.TvmUnexpectedRefReading
import org.usvm.test.resolver.TvmExecutionWithStructuralError
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddrAndUIntTests {
    private val typesPath = "/demo-tlb/addr_and_uint/types.json"
    private val correctPath = "/demo-tlb/addr_and_uint/correct.fc"
    private val intInsteadOfUIntPath = "/demo-tlb/addr_and_uint/int_instead_of_uint.fc"
    private val prematureEndParsePath = "/demo-tlb/addr_and_uint/premature_end_parse.fc"
    private val unexpectedLoadRefPath = "/demo-tlb/addr_and_uint/unexpected_load_ref.fc"

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
    fun testIntInsteadOfUInt() {
        val path = extractResource(intInsteadOfUIntPath)
        val inputInfo = extractTlbInfo(typesPath, this::class)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        assertTrue(tests.all { it.result !is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmTestFailure })

        propertiesFound(
            tests,
            listOf { test ->
                val result = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                result.exit is TvmReadingOfUnexpectedType
            },
        )
    }

    @Test
    fun testPrematureEndParse() {
        val path = extractResource(prematureEndParsePath)
        val inputInfo = extractTlbInfo(typesPath, this::class)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        assertTrue(tests.all { it.result !is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmTestFailure })

        propertiesFound(
            tests,
            listOf { test ->
                val result = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                result.exit is TvmUnexpectedEndOfReading
            },
        )
    }

    @Test
    fun testUnexpectedLoadRef() {
        val path = extractResource(unexpectedLoadRefPath)
        val inputInfo = extractTlbInfo(typesPath, this::class)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        assertTrue(tests.all { it.result !is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmTestFailure })

        propertiesFound(
            tests,
            listOf { test ->
                val result = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                result.exit is TvmUnexpectedRefReading
            },
        )
    }
}
