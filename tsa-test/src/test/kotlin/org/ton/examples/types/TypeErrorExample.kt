package org.ton.examples.types

import org.ton.examples.funcCompileAndAnalyzeAllMethods
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeErrorExample {
    private val path = "/types/type_error.fc"

    @Test
    fun testTypeError() {
        val resourcePath = this::class.java.getResource(path)?.path?.let { Path(it) }
            ?: error("Cannot find resource $path")

        val results = funcCompileAndAnalyzeAllMethods(resourcePath)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.any { it.result is TvmMethodFailure })
    }
}