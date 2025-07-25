package org.ton.examples.cell

import org.ton.examples.compareSymbolicAndConcreteResults
import org.ton.examples.compileAndAnalyzeFift
import org.ton.examples.testConcreteOptions
import org.ton.examples.runFiftMethod
import kotlin.io.path.Path
import kotlin.test.Test

class CellParseTest {
    private val cellParseFiftPath: String = "/cell/CellParse.fif"
    private val cellParseFiftFailurePath: String = "/cell/CellParseFailure.fif"
    private val slicePushFiftPath: String = "/cell/SlicePush.fif"
    private val loadGramsFiftPath: String = "/cell/load_grams.fif"

    @Test
    fun cellParseTest() {
        val fiftResourcePath = this::class.java.getResource(cellParseFiftPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $cellParseFiftPath")

        val symbolicResult = compileAndAnalyzeFift(
            fiftResourcePath,
            tvmOptions = testConcreteOptions,
        )
        val methodIds = (0..12).toSet()

        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }

    @Test
    fun cellLoadIntFailureTest() {
        val fiftResourcePath = this::class.java.getResource(cellParseFiftFailurePath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $cellParseFiftFailurePath")

        val symbolicResult = compileAndAnalyzeFift(
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
        val fiftResourcePath = this::class.java.getResource(slicePushFiftPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $slicePushFiftPath")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)
        val methodIds = (0..1).toSet()

        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }

    @Test
    fun loadGramsTest() {
        val fiftResourcePath = this::class.java.getResource(loadGramsFiftPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $loadGramsFiftPath")

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