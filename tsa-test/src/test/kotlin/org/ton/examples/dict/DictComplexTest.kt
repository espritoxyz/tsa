package org.ton.examples.dict

import org.ton.test.utils.compareSymbolicAndConcreteResultsFunc
import kotlin.test.Test

class DictComplexTest {
    private val nearestPath: String = "/dict/nearest.fc"
    private val minmaxPath: String = "/dict/minmax.fc"

    @Test
    fun nearestTest() {
        compareSymbolicAndConcreteResultsFunc(nearestPath, methods = setOf(0, 1))
    }

    @Test
    fun minmaxTest() {
        compareSymbolicAndConcreteResultsFunc(minmaxPath, methods = setOf(0))
    }
}
