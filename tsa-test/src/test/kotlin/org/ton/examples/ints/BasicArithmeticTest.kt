package org.ton.examples.ints

import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
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
