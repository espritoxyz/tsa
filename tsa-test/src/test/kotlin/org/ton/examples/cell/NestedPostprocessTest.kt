package org.ton.examples.cell

import org.ton.test.utils.compareSymbolicAndConcreteResultsFunc
import kotlin.test.Test

class NestedPostprocessTest {
    private val postprocessOrderTest: String = "/cell/postprocess/postprocess-order.fc"
    private val depthNestedRefTest: String = "/cell/postprocess/postprocess-depth-nested-ref.fc"
    private val cdatasizeDepTest: String = "/cell/postprocess/postprocess-cdatasize-dep.fc"

    @Test
    fun cellDepthValueTest() {
        compareSymbolicAndConcreteResultsFunc(postprocessOrderTest, methods = setOf(0, 1))
    }

    @Test
    fun cellHashOfDeferredValuesTest() {
        compareSymbolicAndConcreteResultsFunc(postprocessOrderTest, methods = setOf(2, 3))
    }

    @Test
    fun nestedCellHashTest() {
        compareSymbolicAndConcreteResultsFunc(postprocessOrderTest, methods = setOf(4))
    }

    @Test
    fun `dictionary-hash-in-path-constraint`() {
        compareSymbolicAndConcreteResultsFunc(postprocessOrderTest, methods = setOf(5))
    }

    @Test
    fun depthOfNestedHashTest() {
        compareSymbolicAndConcreteResultsFunc(depthNestedRefTest, methods = setOf(0))
    }

    @Test
    fun hashOfCdatasizeTest() {
        compareSymbolicAndConcreteResultsFunc(cdatasizeDepTest, methods = setOf(0))
    }
}
