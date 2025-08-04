package org.ton.examples.cell

import org.ton.examples.compareSymbolicAndConcreteFromResource
import kotlin.test.Test

class CellBuildTest {
    private val storeSliceConstFif: String = "/cell/cell-build/CellBuild.fif"

    @Test
    fun cellBuildTest() {
        compareSymbolicAndConcreteFromResource(testPath = storeSliceConstFif, lastMethodIndex = 6)
    }
}