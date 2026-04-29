package org.ton.examples.cell

import org.ton.test.utils.compareSymbolicAndConcreteResultsFift
import org.ton.test.utils.compareSymbolicAndConcreteResultsFunc
import kotlin.test.Test

class CellParseTest {
    private val cellParseFiftPath: String = "/cell/CellParse.fif"
    private val cellParseFiftFailurePath: String = "/cell/CellParseFailure.fif"
    private val slicePushFiftPath: String = "/cell/SlicePush.fif"
    private val loadGramsFiftPath: String = "/cell/load_grams.fif"

    private val splitFuncs: String = "/cell/parse/split.fc"
    private val cutfirstFunc: String = "/cell/parse/scutfirst.fc"
    private val skipfirst: String = "/cell/parse/sskipfirst.fc"

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
    fun `test split functions`() {
        compareSymbolicAndConcreteResultsFunc(splitFuncs, (0..9).toSet())
    }

    @Test
    fun `cut first functions`() {
        compareSymbolicAndConcreteResultsFunc(cutfirstFunc, (0..8).toSet())
    }

    @Test
    fun `skip first functions`() {
        compareSymbolicAndConcreteResultsFunc(skipfirst, (0..8).toSet())
    }
}
