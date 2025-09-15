package org.ton.examples.exceptions

import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.ton.test.utils.testOptionsToAnalyzeSpecificMethod
import org.usvm.machine.state.TvmFailureType
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmTestSliceValue
import kotlin.test.Test
import kotlin.test.assertEquals

class ExceptionTypesTest {
    private val cellSizeConflictPath = "/exception-types/cell_size_conflict.fc"
    private val cellSizeConflict2Path = "/exception-types/cell_size_conflict_2.fc"
    private val simpleStructuralPath = "/exception-types/simple_structural.fc"
    private val simpleSymbolicStructuralPath = "/exception-types/simple_symbolic_structural.fc"
    private val simpleStructuralRefPath = "/exception-types/simple_structural_ref.fc"
    private val allocatedSamplePath = "/exception-types/allocated_sample.fc"
    private val mixedItePath = "/exception-types/mixed_ite_sample.fc"

    @Test
    fun testCellSizeConflict() {
        val resourcePath = extractResource(cellSizeConflictPath)

        val results = funcCompileAndAnalyzeAllMethods(resourcePath)
        assertEquals(1, results.testSuites.size)
        val testSuite = results.testSuites.first()

        checkInvariants(
            testSuite,
            listOf { test ->
                (test.result as? TvmMethodFailure)?.failure?.type != TvmFailureType.FixedStructuralError
            },
        )

        propertiesFound(
            testSuite,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.failure?.type == TvmFailureType.RealError },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun testCellSizeConflict2() {
        val resourcePath = extractResource(cellSizeConflict2Path)

        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions = testOptionsToAnalyzeSpecificMethod,
            )
        assertEquals(1, results.testSuites.size)
        val testSuite = results.testSuites.first()

        checkInvariants(
            testSuite,
            listOf(
                { test ->
                    val param =
                        (test.input.usedParameters.lastOrNull() as? TvmTestSliceValue)?.cell
                            ?: return@listOf false
                    val isEmpty = param.data.isEmpty() && param.refs.isEmpty()
                    !isEmpty || (test.result as? TvmMethodFailure)?.failure?.type != TvmFailureType.FixedStructuralError
                },
                { test -> test.result !is TvmSuccessfulExecution },
            ),
        )

        propertiesFound(
            testSuite,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.failure?.type == TvmFailureType.FixedStructuralError },
                { test -> (test.result as? TvmMethodFailure)?.failure?.type == TvmFailureType.RealError },
            ),
        )
    }

    @Test
    fun testSimpleStructural() {
        val resourcePath = extractResource(simpleStructuralPath)

        val results = funcCompileAndAnalyzeAllMethods(resourcePath)
        assertEquals(1, results.testSuites.size)
        val testSuite = results.testSuites.first()

        checkInvariants(
            testSuite,
            listOf { test ->
                val type = (test.result as? TvmMethodFailure)?.failure?.type
                type != TvmFailureType.RealError && type != TvmFailureType.UnknownError
            },
        )

        propertiesFound(
            testSuite,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.failure?.type == TvmFailureType.FixedStructuralError },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun testSimpleSymbolicStructural() {
        val resourcePath = extractResource(simpleSymbolicStructuralPath)

        val results = funcCompileAndAnalyzeAllMethods(resourcePath)
        assertEquals(1, results.testSuites.size)
        val testSuite = results.testSuites.first()

        checkInvariants(
            testSuite,
            listOf { test ->
                val type = (test.result as? TvmMethodFailure)?.failure?.type
                type != TvmFailureType.RealError && type != TvmFailureType.UnknownError
            },
        )

        propertiesFound(
            testSuite,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.failure?.type == TvmFailureType.FixedStructuralError },
                { test -> (test.result as? TvmMethodFailure)?.failure?.type == TvmFailureType.SymbolicStructuralError },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun testSimpleStructuralRef() {
        val resourcePath = extractResource(simpleStructuralRefPath)

        val results = funcCompileAndAnalyzeAllMethods(resourcePath)
        assertEquals(1, results.testSuites.size)
        val testSuite = results.testSuites.first()

        checkInvariants(
            testSuite,
            listOf { test ->
                val type = (test.result as? TvmMethodFailure)?.failure?.type
                type != TvmFailureType.RealError && type != TvmFailureType.UnknownError
            },
        )

        propertiesFound(
            testSuite,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.failure?.type == TvmFailureType.FixedStructuralError },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun testAllocatedSample() {
        val resourcePath = extractResource(allocatedSamplePath)

        val results = funcCompileAndAnalyzeAllMethods(resourcePath)
        assertEquals(1, results.testSuites.size)
        val testSuite = results.testSuites.first()

        checkInvariants(
            testSuite,
            listOf { test ->
                val type = (test.result as? TvmMethodFailure)?.failure?.type
                type != TvmFailureType.UnknownError
            },
        )

        propertiesFound(
            testSuite,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.failure?.type == TvmFailureType.FixedStructuralError },
                { test -> (test.result as? TvmMethodFailure)?.failure?.type == TvmFailureType.RealError },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }

    @Test
    fun testMixedIte() {
        val resourcePath = extractResource(mixedItePath)

        val results = funcCompileAndAnalyzeAllMethods(resourcePath)
        assertEquals(1, results.testSuites.size)
        val testSuite = results.testSuites.first()

        propertiesFound(
            testSuite,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.failure?.type == TvmFailureType.FixedStructuralError },
                { test -> (test.result as? TvmMethodFailure)?.failure?.type == TvmFailureType.RealError },
                { test -> test.result is TvmSuccessfulExecution },
            ),
        )
    }
}
