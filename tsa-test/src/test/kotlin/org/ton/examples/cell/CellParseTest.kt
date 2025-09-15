package org.ton.examples.cell

import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.extractResource
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
import kotlin.test.Test

class CellParseTest {
    private val cellParseFiftPath: String = "/cell/CellParse.fif"
    private val cellParseFiftFailurePath: String = "/cell/CellParseFailure.fif"
    private val slicePushFiftPath: String = "/cell/SlicePush.fif"
    private val loadGramsFiftPath: String = "/cell/load_grams.fif"

    @Test
    fun cellParseTest() {
        val fiftResourcePath = extractResource(cellParseFiftPath)

        val symbolicResult =
            compileAndAnalyzeFift(
                fiftResourcePath,
                tvmOptions = testConcreteOptions,
            )
        val methodIds = (0..15).toSet()

        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }

    @Test
    fun cellLoadIntFailureTest() {
        val fiftResourcePath = extractResource(cellParseFiftFailurePath)

        val symbolicResult =
            compileAndAnalyzeFift(
                fiftResourcePath,
                tvmOptions = testConcreteOptions,
            )
        val methodIds = (0..6).toSet()

        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }

    @Test
    fun slicePushTest() {
        val fiftResourcePath = extractResource(slicePushFiftPath)

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)
        val methodIds = (0..1).toSet()

        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }

    @Test
    fun loadGramsTest() {
        val fiftResourcePath = extractResource(loadGramsFiftPath)

        val symbolicResult =
            compileAndAnalyzeFift(
                fiftResourcePath,
                tvmOptions = testConcreteOptions,
            )
        val methodIds = (0..1).toSet()

        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}
