package org.ton.examples.stack

import org.junit.jupiter.api.Test
import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
import kotlin.io.path.Path

class StackPopTest {
    private val fiftPath: String = "/stack/StackPop.fif"

    @Test
    fun testPopCommand() {
        val fiftResourcePath = this::class.java.getResource(fiftPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $fiftPath")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        compareSymbolicAndConcreteResults(methodIds = setOf(0, 1), symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}