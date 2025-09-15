package org.ton.examples.types

import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.testOptionsToAnalyzeSpecificMethod
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestSliceValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimpleMaybe {
    private val path = "/types/maybe.fc"

    @Test
    fun testSimpleMaybe() {
        val resourcePath = extractResource(path)

        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions = testOptionsToAnalyzeSpecificMethod,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.any { it.result is TvmMethodFailure })

        checkInvariants(
            tests,
            listOf { test ->
                val param = test.input.usedParameters.lastOrNull() as? TvmTestSliceValue ?: return@listOf true
                val someCell = param.cell
                val anotherCell = someCell.refs.firstOrNull() ?: return@listOf true
                anotherCell is TvmTestDataCellValue
            },
        )
    }
}
