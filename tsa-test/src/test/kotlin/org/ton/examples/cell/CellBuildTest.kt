package org.ton.examples.cell

import org.ton.examples.compareSymbolicAndConcreteResults
import org.ton.examples.compileAndAnalyzeFift
import org.ton.examples.extractResource
import org.ton.examples.runFiftMethod
import org.ton.examples.testConcreteOptions
import kotlin.test.Test

class CellBuildTest {
    private val storeSliceConstFif: String = "/cell/cell-build/CellBuild.fif"

    @Test
    fun cellBuildTest() {
        val fiftResourcePath = extractResource(storeSliceConstFif)

        val symbolicResult = compileAndAnalyzeFift(
            fiftResourcePath,
            tvmOptions = testConcreteOptions,
        )
        val methodIds = (0..1).toSet()

        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}