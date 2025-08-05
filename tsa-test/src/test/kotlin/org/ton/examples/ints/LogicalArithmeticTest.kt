package org.ton.examples.ints

import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
import kotlin.io.path.Path
import kotlin.test.Test

class LogicalArithmeticTest {
    private val logicalArithFiftPath: String = "/ints/logical_arith.fif"
    private val logicalArithFailureFiftPath: String = "/ints/logical_arith_failure.fif"

    @Test
    fun logicalArithResultTest() {
        val fiftResourcePath = this::class.java.getResource(logicalArithFiftPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $logicalArithFiftPath")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..18).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }

    @Test
    fun logicalArithFailureTest() {
        val fiftResourcePath = this::class.java.getResource(logicalArithFailureFiftPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $logicalArithFailureFiftPath")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..14).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}