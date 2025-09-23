package org.ton.examples.types

import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeErrorExample {
    private val path = "/types/type_error.fc"

    @Test
    fun testTypeError() {
        val resourcePath = extractResource(path)

        val results = funcCompileAndAnalyzeAllMethods(resourcePath)
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()
        assertTrue(tests.any { it.result is TvmSuccessfulExecution })
        assertTrue(tests.any { it.result is TvmMethodFailure })
    }
}
