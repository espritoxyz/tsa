package org.ton.examples.exceptions

import org.ton.examples.compareSymbolicAndConcreteResults
import org.ton.examples.compileAndAnalyzeFift
import org.ton.examples.testConcreteOptions
import org.ton.examples.runFiftMethod
import kotlin.io.path.Path
import kotlin.test.Test

class ExceptionsTest {
    private val exceptionsFiftPath: String = "/exceptions/Exceptions.fif"

    @Test
    fun testExceptions() {
        val fiftResourcePath = this::class.java.getResource(exceptionsFiftPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $exceptionsFiftPath")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..5).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}