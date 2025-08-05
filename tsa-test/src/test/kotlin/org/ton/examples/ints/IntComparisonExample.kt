package org.ton.examples.ints

import org.junit.jupiter.api.Test
import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
import org.usvm.test.resolver.TvmTestIntegerValue
import kotlin.io.path.Path
import kotlin.test.assertEquals

class IntComparisonExample {
    private val sourcesPath: String = "/ints/int_comparison_no_throw.fc"
    private val fiftPath: String = "/ints/Comparison.fif"

    @Test
    fun testIntComparisonExamples() {
        val sourceResourcePath = this::class.java.getResource(sourcesPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource source $sourcesPath")

        val symbolicResult = funcCompileAndAnalyzeAllMethods(sourceResourcePath)
        assertEquals(13, symbolicResult.testSuites.size)
        symbolicResult.forEach { (methodId, _, tests) ->
            if (methodId.toInt() == 0)
                return@forEach
            val results = tests.flatMap { test ->
                test.result.stack.map { (it as TvmTestIntegerValue).value.toInt() }
            }.sorted()
            assertEquals(listOf(1, 2), results)
        }
    }

    @Test
    fun testIntComparisonFift() {
        val fiftResourcePath = this::class.java.getResource(fiftPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $sourcesPath")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..13).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}