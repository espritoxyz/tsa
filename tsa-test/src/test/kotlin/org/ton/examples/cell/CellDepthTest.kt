package org.ton.examples.cell

import org.ton.test.utils.compareSymbolicAndConcreteResultsFunc
import kotlin.test.Test

class CellDepthTest {
    private val cellDepthPath: String = "/cell/depth/cell-depth.fc"
    private val unreachableCellDepthPath: String = "/cell/depth/cell-depth-unreachable.fc"

    @Test
    fun cellDepthValueTest() {
        compareSymbolicAndConcreteResultsFunc(cellDepthPath, methods = setOf(0, 1))
    }

    @Test
    fun droppedStateTest() {
        compareSymbolicAndConcreteResultsFunc(unreachableCellDepthPath, methods = setOf(0))
    }
}
