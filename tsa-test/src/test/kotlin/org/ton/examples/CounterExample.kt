package org.ton.examples

import kotlin.test.Test
import kotlin.test.assertTrue

class CounterExample {
    // TODO fix
    private val bytecodePath: String = "/counter.txt"

    @Test
    fun testCounter() {
        val bytecodeResourcePath = this::class.java.getResource(bytecodePath)?.path
            ?: error("Cannot find resource bytecode $bytecodePath")

        val symbolicResult = analyzeAllMethods(bytecodeResourcePath)
        val allTests = symbolicResult.map { it.tests }.flatten()
        val results = allTests.map { it.result }
        assertTrue(results.isNotEmpty())
    }
}
