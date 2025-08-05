package org.ton.examples.loops

import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
import kotlin.io.path.Path
import kotlin.test.Test

class AgainLoopTest {
    private val againLoopsFiftPath: String = "/loops/AgainLoops.fif"

    @Test
    fun testAgainLoops() {
        val fiftResourcePath = this::class.java.getResource(againLoopsFiftPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $againLoopsFiftPath")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..3).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}
