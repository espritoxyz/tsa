package org.ton.examples.conditions

import org.ton.examples.compareSymbolicAndConcreteResults
import org.ton.examples.compileAndAnalyzeFift
import org.ton.examples.runFiftMethod
import org.ton.examples.testConcreteOptions
import kotlin.io.path.Path
import kotlin.test.Test

class IfConditionTest {
    private val ifConditionsFiftPath: String = "/conditions/IfCondition.fif"

    @Test
    fun testIfConditions() {
        val fiftResourcePath = this::class.java.getResource(ifConditionsFiftPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $ifConditionsFiftPath")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..5).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}
