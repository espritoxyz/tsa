package org.ton.examples.exceptions

import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.testConcreteOptions
import org.ton.test.utils.runFiftMethod
import kotlin.io.path.Path
import kotlin.test.Test

class ExceptionsTest {
    private val exceptionsFiftPath: String = "/exceptions/Exceptions.fif"
    private val exceptionsInstructionsFiftPath: String = "/exceptions/ExceptionInstructions.fif"

    @Test
    fun testExceptions() {
        compareSymbolicAndConcreteFromResource(testPath = exceptionsFiftPath, lastMethodIndex = 5)
    }

    @Test
    fun testExceptionInstructions() {
        compareSymbolicAndConcreteFromResource(
            testPath = exceptionsInstructionsFiftPath,
            lastMethodIndex = 13
        )
    }

    private fun compareSymbolicAndConcreteFromResource(testPath: String, lastMethodIndex: Int) {
        val fiftResourcePath = this::class.java.getResource(testPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $testPath")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..lastMethodIndex).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}