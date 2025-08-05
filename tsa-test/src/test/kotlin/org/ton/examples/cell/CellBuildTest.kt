package org.ton.examples.cell

import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.extractResource
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
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