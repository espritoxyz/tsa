package org.ton.resolver

import org.ton.bigint.plus
import org.ton.bytecode.TvmArithmDivInst
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.usvm.machine.state.TvmIntegerOverflowError
import org.usvm.test.resolver.TvmContractSymbolicTestResult
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmTestBuilderValue
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestIntegerValue
import org.usvm.test.resolver.TvmTestSliceValue
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestResolverTest {
    private val divisionByIntZeroSource = "/division/div-by-zero/div_by_zero.fc"
    private val divisionByCellRefZeroSource = "/division/div-by-cell-ref-number/div-by-cell-ref-number.fc"
    private val divisionBySliceRefZeroSource = "/division/div-by-cell-ref-number/div-by-slice-ref-number.fc"
    private val divisionByBuilderRefZeroSource = "/division/div-by-cell-ref-number/div-by-builder-ref-number.fc"

    @Test
    fun testDivisionByIntZero() {
        val symbolicResult = compileAndAnalyzeAllMethods(divisionByIntZeroSource)
        val (_, test) =
            findMethodTest(symbolicResult) { test ->
                (test.result as? TvmMethodFailure)?.let {
                    it.failure.exit is TvmIntegerOverflowError && it.lastStmt is TvmArithmDivInst
                } == true
            }

        val usedParameters = test.input.usedParameters
        assertTrue(usedParameters.size == 2)

        val arg0 = (usedParameters[0] as TvmTestIntegerValue).value
        val arg1 = (usedParameters[1] as TvmTestIntegerValue).value

        assertEquals(BigInteger.ZERO, arg0 - (arg1 + 3))
    }

    @Test
    fun testDivisionByCellRefZero() {
        val symbolicResult = compileAndAnalyzeAllMethods(divisionByCellRefZeroSource)
        val (_, test) =
            findMethodTest(symbolicResult) { test ->
                (test.result as? TvmMethodFailure)?.let {
                    it.failure.exit is TvmIntegerOverflowError && it.lastStmt is TvmArithmDivInst
                } == true
            }

        val usedParameters = test.input.usedParameters
        assertTrue(usedParameters.size == 1)

        val arg = (usedParameters.first() as TvmTestDataCellValue).refs.first() as TvmTestDataCellValue
        val extractedInt = arg.data.drop(3).take(8)

        assertEquals("00000011", extractedInt)
    }

    @Test
    fun testDivisionBySliceRefZero() {
        val symbolicResult = compileAndAnalyzeAllMethods(divisionBySliceRefZeroSource)
        val (_, test) =
            findMethodTest(symbolicResult) { test ->
                (test.result as? TvmMethodFailure)?.let {
                    it.failure.exit is TvmIntegerOverflowError && it.lastStmt is TvmArithmDivInst
                } == true
            }

        val usedParameters = test.input.usedParameters
        assertTrue(usedParameters.size == 1)

        val arg = (usedParameters.first() as TvmTestSliceValue).cell.refs.first() as TvmTestDataCellValue
        val extractedInt = arg.data.drop(3).take(8)

        assertEquals("00000011", extractedInt)
    }

    @Test
    fun testDivisionByBuilderRefZero() {
        val symbolicResult = compileAndAnalyzeAllMethods(divisionByBuilderRefZeroSource)
        val (_, test) =
            findMethodTest(symbolicResult) { test ->
                (test.result as? TvmMethodFailure)?.let {
                    it.failure.exit is TvmIntegerOverflowError && it.lastStmt is TvmArithmDivInst
                } == true
            }

        val usedParameters = test.input.usedParameters
        assertTrue(usedParameters.size == 1)

        val arg = (usedParameters.first() as TvmTestBuilderValue).refs.first() as TvmTestDataCellValue
        val extractedInt = arg.data.drop(3).take(8)

        assertEquals("00000011", extractedInt)
    }

    private fun compileAndAnalyzeAllMethods(sourcePath: String): TvmContractSymbolicTestResult {
        val bytecodeResourcePath = extractResource(sourcePath)

        return funcCompileAndAnalyzeAllMethods(bytecodeResourcePath)
    }

    private fun findMethodTest(
        symbolicResult: TvmContractSymbolicTestResult,
        predicate: (TvmSymbolicTest) -> Boolean,
    ): Pair<Int, TvmSymbolicTest> =
        symbolicResult.firstNotNullOf { (method, _, tests) ->
            val test =
                tests.firstOrNull(predicate)
                    ?: return@firstNotNullOf null

            method.toInt() to test
        }
}
