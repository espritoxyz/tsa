package org.ton.examples.division

import org.ton.test.utils.analyzeAllMethods
import org.ton.test.utils.extractResource
import org.usvm.machine.state.TvmIntegerOverflowError
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Test
import kotlin.test.assertTrue

class DivisionByZeroExample {
    private val bytecodePath: String = "/division/div-by-constant-zero/boc.txt"

    @Test
    fun testDivisionByZero() {
        val bytecodeResourcePath = extractResource(bytecodePath)

        val symbolicResult = analyzeAllMethods(bytecodeResourcePath.toString())
        val allTests = symbolicResult.map { it.tests }.flatten()
        val results = allTests.map { it.result }
        val exceptions =
            results
                .mapNotNull {
                    (it as? TvmTestFailure)?.failure?.exit
                }.filterIsInstance<TvmIntegerOverflowError>()
        assertTrue(exceptions.isNotEmpty(), "Division by zero was not found!")
    }
}
