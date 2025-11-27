package org.ton.examples.tlb

import org.ton.test.utils.executionCode
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.testOptionsToAnalyzeSpecificMethod
import org.usvm.test.resolver.TvmSymbolicTest
import kotlin.test.Test

class TlbTest {
    fun List<TvmSymbolicTest>.toExitCodes() =
        map {
            it.executionCode()
        }

    @Test
    fun testStore1Store1Load2() {
        val path = "/tlb/store-1-store-1-load-2.fc"
        assertNoTlbMissesInAnalysis(path)
    }

    @Test
    fun testStoreZeroesLoadAddress() {
        val path = "/tlb/store-256-zeroes-load-addr.fc"
        assertNoTlbMissesInAnalysis(path)
    }

    private fun assertNoTlbMissesInAnalysis(path: String) {
        val resourcePath = extractResource(path)
        val symbolicResult =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions = testOptionsToAnalyzeSpecificMethod.copy(collectTlbMemoryStats = true),
            )
        val tests = symbolicResult.testSuites.single()
        val assertionViolations = tests.toExitCodes().filter { it in 400..499 }
        if (assertionViolations.isNotEmpty()) {
            assert(false) { "Found assertion violations: $assertionViolations" }
        }
        assert(tests.toExitCodes().any { it == 500 }) { "No successfull execution found" }
        tests.filter { it.executionCode() == 500 }.forEach {
            assert(it.debugInfo.tlbMemoryMisses == 0) { "TLb memory miss detected" }
        }
    }
}
