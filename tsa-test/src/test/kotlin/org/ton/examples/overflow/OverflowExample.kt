package org.ton.examples.overflow

import org.ton.examples.analyzeAllMethods
import org.usvm.machine.state.TvmIntegerOverflowError
import org.usvm.test.resolver.TvmMethodFailure
import kotlin.test.Test
import kotlin.test.assertTrue

class OverflowExample {
    private val bytecodePath: String = "/overflow/add-overflow/boc.txt"

    @Test
    fun testAddOverflowSymbolic() {
        val bytecodeResourcePath = this::class.java.getResource(bytecodePath)?.path
            ?: error("Cannot find resource bytecode $bytecodePath")

        val symbolicResult = analyzeAllMethods(bytecodeResourcePath)
        val allTests = symbolicResult.map { it.tests }.flatten()
        val results = allTests.map { it.result }
        val exceptions = results.mapNotNull { (it as? TvmMethodFailure)?.failure?.exit }.filterIsInstance<TvmIntegerOverflowError>()
        assertTrue(exceptions.isNotEmpty(), "Integer overflow was not found!")
    }
}
