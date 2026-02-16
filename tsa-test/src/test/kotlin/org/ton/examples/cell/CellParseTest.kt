package org.ton.examples.cell

import org.ton.test.utils.compareSymbolicAndConcreteResultsFift
import kotlin.test.Test

class CellParseTest {
    private val cellParseFiftPath: String = "/cell/CellParse.fif"
    private val cellParseFiftFailurePath: String = "/cell/CellParseFailure.fif"
    private val slicePushFiftPath: String = "/cell/SlicePush.fif"
    private val loadGramsFiftPath: String = "/cell/load_grams.fif"
    private val sdppfxrevFiftPath: String = "/cell/sdppfxrev.fif"

    @Test
    fun cellParseTest() {
        compareSymbolicAndConcreteResultsFift(cellParseFiftPath, 15)
    }

    @Test
    fun cellLoadIntFailureTest() {
        compareSymbolicAndConcreteResultsFift(cellParseFiftFailurePath, 6)
    }

    @Test
    fun slicePushTest() {
        compareSymbolicAndConcreteResultsFift(slicePushFiftPath, 1)
    }

    @Test
    fun loadGramsTest() {
        compareSymbolicAndConcreteResultsFift(loadGramsFiftPath, 1)
    }

    @Test
    fun `isPrefix test`() {
        compareSymbolicAndConcreteResultsFift(sdppfxrevFiftPath, 1)
    }
}
