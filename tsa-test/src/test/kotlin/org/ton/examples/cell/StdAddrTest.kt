package org.ton.examples.cell

import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.extractResource
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
import kotlin.test.Test

class StdAddrTest {
    private val cellStdAddrFiftPath: String = "/cell/StdAddr.fif"

    @Test
    fun stStdAddrTests() {
        val fiftResourcePath = extractResource(cellStdAddrFiftPath)

        val methodIds = (0..12).toSet()
        val symbolicResult =
            compileAndAnalyzeFift(
                fiftResourcePath,
                tvmOptions = testConcreteOptions,
            )

        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}
