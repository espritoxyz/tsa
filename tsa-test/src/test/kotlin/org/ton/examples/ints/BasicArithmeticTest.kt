package org.ton.examples.ints

import org.ton.examples.compareSymbolicAndConcreteResults
import org.ton.examples.compileAndAnalyzeFift
import org.ton.examples.runFiftMethod
import org.ton.examples.testConcreteOptions
import kotlin.io.path.Path
import kotlin.test.Test

class BasicArithmeticTest {
    private val fiftSourcesPath: String = "/ints/basic_arith.fif"

    @Test
    fun basicArithResultTest() {
        val fiftResourcePath = this::class.java.getResource(fiftSourcesPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource func $fiftSourcesPath")
        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..23).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}
