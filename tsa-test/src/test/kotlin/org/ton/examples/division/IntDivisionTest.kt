package org.ton.examples.division

import org.junit.jupiter.api.Test
import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
import kotlin.io.path.Path

class IntDivisionTest {
    private val fiftPath = "/division/int_division_no_throw.fif"
    private val fiftPathBasic = "/division/int_division_basic.fif"
    private val fiftPathFail = "/division/int_division_fail.fif"

    @Test
    fun testIntDivisionFiftBasic() {
        val fiftResourcePath = this::class.java.getResource(fiftPathBasic)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $fiftPathBasic")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..30).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }

    @Test
    fun testIntDivisionFiftNoThrow() {
        val fiftResourcePath = this::class.java.getResource(fiftPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $fiftPath")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..190).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }

    @Test
    fun testIntDivisionFiftFailure() {
        val fiftResourcePath = this::class.java.getResource(fiftPathFail)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $fiftPathFail")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..96).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}