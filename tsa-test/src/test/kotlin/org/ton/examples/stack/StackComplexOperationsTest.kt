package org.ton.examples.stack

import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.extractResource
import org.ton.test.utils.loadIntegers
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
import org.usvm.machine.TvmComponents
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.state.TvmStack
import org.usvm.machine.state.addInt
import kotlin.test.Test
import kotlin.test.assertEquals

class StackComplexOperationsTest {
    private val ctx = TvmContext(testConcreteOptions, TvmComponents(TvmOptions()))

    private val stackComplexFiftPath: String = "/stack/StackComplex.fif"
    private val stackNullChecksFiftPath: String = "/stack/NullChecks.fif"

    @Test
    fun testStackReverse(): Unit =
        with(ctx) {
            val stack = TvmStack(ctx)

            val originalOrder = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            val reversed53Order = listOf(0, 1, 2, 7, 6, 5, 4, 3, 8, 9, 10)

            originalOrder.forEach { stack.addInt(it.toBv257()) }

            stack.reverse(5, 3)

            val stackState = stack.loadIntegers(originalOrder.size)
            assertEquals(reversed53Order, stackState)
        }

    @Test
    fun testStackNullChecks() {
        val fiftResourcePath = extractResource(stackNullChecksFiftPath)

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..15).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }

    @Test
    fun testStackComplexFift() {
        val fiftResourcePath = extractResource(stackComplexFiftPath)

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..30).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}
