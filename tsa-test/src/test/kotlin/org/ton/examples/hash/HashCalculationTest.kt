package org.ton.examples.hash

import org.junit.jupiter.api.Test
import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
import kotlin.io.path.Path

class HashCalculationTest {
    private val fiftPath = "/hash/hash_calc.fif"

    @Test
    fun testHashCalculation() {
        val fiftResourcePath = this::class.java.getResource(fiftPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $fiftPath")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..4).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}