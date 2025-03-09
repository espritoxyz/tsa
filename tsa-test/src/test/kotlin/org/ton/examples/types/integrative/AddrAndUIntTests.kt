package org.ton.examples.types.integrative

import org.ton.examples.checkInvariants
import org.ton.examples.extractTlbInfo
import org.ton.examples.funcCompileAndAnalyzeAllMethods
import org.ton.examples.propertiesFound
import org.usvm.machine.types.TvmReadingOfUnexpectedType
import org.usvm.machine.types.TvmUnexpectedEndOfReading
import org.usvm.machine.types.TvmUnexpectedRefReading
import org.usvm.test.resolver.TvmExecutionWithStructuralError
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import kotlin.io.path.Path
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
    fun testIntInsteadOfUInt() {
        val path = this::class.java.getResource(intInsteadOfUIntPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource bytecode $intInsteadOfUIntPath")
        val inputInfo = extractTlbInfo(typesPath)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        assertTrue(tests.all { it.result !is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        propertiesFound(
            tests,
            listOf { test ->
                val result = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                result.exit is TvmReadingOfUnexpectedType
            }
        )
    }

    @Test
    fun testPrematureEndParse() {
        val path = this::class.java.getResource(prematureEndParsePath)?.path?.let { Path(it) }
            ?: error("Cannot find resource bytecode $prematureEndParsePath")
        val inputInfo = extractTlbInfo(typesPath)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        assertTrue(tests.all { it.result !is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        propertiesFound(
            tests,
            listOf { test ->
                val result = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                result.exit is TvmUnexpectedEndOfReading
            }
        )
    }

    @Test
    fun testUnexpectedLoadRef() {
        val path = this::class.java.getResource(unexpectedLoadRefPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource bytecode $unexpectedLoadRefPath")
        val inputInfo = extractTlbInfo(typesPath)

        val results = funcCompileAndAnalyzeAllMethods(path, inputInfo = inputInfo)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        assertTrue(tests.all { it.result !is TvmSuccessfulExecution })
        assertTrue(tests.all { it.result !is TvmMethodFailure })

        propertiesFound(
            tests,
            listOf { test ->
                val result = test.result as? TvmExecutionWithStructuralError ?: return@listOf false
                result.exit is TvmUnexpectedRefReading
            }
        )
    }
}
