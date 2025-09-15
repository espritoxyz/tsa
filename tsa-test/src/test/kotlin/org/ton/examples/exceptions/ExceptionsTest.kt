package org.ton.examples.exceptions

import org.ton.test.utils.compareSymbolicAndConcreteFromResource
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
            lastMethodIndex = 13,
        )
    }
}
