package org.ton.examples.loops

import org.ton.examples.compareSymbolicAndConcreteResults
import org.ton.examples.compileAndAnalyzeFift
import org.ton.examples.runFiftMethod
import org.ton.examples.testConcreteOptions
import kotlin.io.path.Path
import kotlin.test.Test

class WhileLoopTest {
    private val whileLoopsFiftPath: String = "/loops/WhileLoops.fif"

    @Test
    fun testWhileLoops() {
        val fiftResourcePath = this::class.java.getResource(whileLoopsFiftPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $whileLoopsFiftPath")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..5).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}
