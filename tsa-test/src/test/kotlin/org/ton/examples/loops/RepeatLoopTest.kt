package org.ton.examples.loops

import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
import kotlin.io.path.Path
import kotlin.test.Test

class RepeatLoopTest {
    private val repeatLoopsFiftPath: String = "/loops/RepeatLoops.fif"

    @Test
    fun testRepeatLoops() {
        val fiftResourcePath = this::class.java.getResource(repeatLoopsFiftPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $repeatLoopsFiftPath")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..6).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}
