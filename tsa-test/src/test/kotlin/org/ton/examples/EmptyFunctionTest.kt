package org.ton.examples

import org.ton.test.utils.compareSymbolicAndConcreteResultsFunc
import kotlin.test.Test

class EmptyFunctionTest {
    private val storeOnesFunc: String = "/empty.fc"

    @Test
    fun emptyFunctionDoesNotCrash() {
        compareSymbolicAndConcreteResultsFunc(storeOnesFunc, setOf(0))
    }
}