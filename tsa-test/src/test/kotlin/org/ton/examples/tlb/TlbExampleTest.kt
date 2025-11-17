package org.ton.examples.tlb

import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.testOptionsToAnalyzeSpecificMethod
import org.usvm.machine.getResourcePathEasy
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import kotlin.test.Test
import kotlin.test.assertTrue

class TlbTest {
    @Test
    fun testAddAndRemove() {
        val resourcePath = getResourcePathEasy("/tlb/store-1-store-1-load-2.fc")

        val symbolicResult =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions = testOptionsToAnalyzeSpecificMethod.copy(collectTlbMemoryStats = true),
            )
        val tests = symbolicResult.testSuites.single()
        assertTrue { tests.all { it.result !is TvmSuccessfulExecution } }
        tests.forEach { test ->
            val result = test.result
            if (result is TvmMethodFailure && result.exitCode == 300) {
                assert(test.debugInfo.tlbMemoryMisses == 0)
            }
        }
    }
}
