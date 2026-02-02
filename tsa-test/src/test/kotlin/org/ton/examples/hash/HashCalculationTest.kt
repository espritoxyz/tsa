package org.ton.examples.hash

import org.junit.jupiter.api.Test
import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.extractResource
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions

class HashCalculationTest {
    private val fiftPath = "/hash/hash_calc.fif"

    @Test
    fun testHashCalculation() {
        val fiftResourcePath = extractResource(fiftPath)

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..7).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}
