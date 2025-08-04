package org.ton.examples.types

import org.junit.jupiter.api.Test
import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
import org.ton.test.utils.testOptionsToAnalyzeSpecificMethod
import java.math.BigInteger
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BadTypesTests {
    private val fiftPath = "/fift-with-input/bad_types.fif"
    private val fiftErrorsPath = "/types/type_errors.fif"

    @Test
    fun testBadTypes() {
        val fiftResourcePath = this::class.java.getResource(fiftPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $fiftPath")

        val symbolicResult = compileAndAnalyzeFift(
            fiftResourcePath,
            methodsBlackList = setOf(BigInteger.ZERO),
            tvmOptions = testOptionsToAnalyzeSpecificMethod
        )

        assertEquals(1, symbolicResult.testSuites.size)

        symbolicResult.testSuites.forEach {
            assertTrue(it.tests.isNotEmpty())
        }
    }

    @Test
    fun testFiftTypeErrors() {
        val fiftResourcePath = this::class.java.getResource(fiftErrorsPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource fift $fiftErrorsPath")

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..1).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}