package org.ton.examples.tact

import org.junit.jupiter.api.assertThrows
import org.ton.bytecode.TsaContractCode
import org.usvm.machine.BocAnalyzer
import org.usvm.machine.NoSelectedMethodsToAnalyze
import org.usvm.machine.analyzeAllMethods
import org.usvm.machine.analyzeSpecificMethod
import org.usvm.machine.getResourcePath
import org.usvm.machine.state.TvmIntegerOverflowError
import org.usvm.machine.toMethodId
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Test
import kotlin.test.assertTrue

class TactWithInlinedMethods {
    private val pathToBoc = "/contracts/sample_Divider.code.boc"
    private val resourcePath = getResourcePath<TactDividerTest>(pathToBoc)

    private val code: TsaContractCode by lazy {
        BocAnalyzer.loadContractFromBoc(resourcePath)
    }

    @Test
    fun testDivider() {
        val symbolicResult = analyzeSpecificMethod(code, 95202.toMethodId())

        val results = symbolicResult.map { it.result }
        val exceptions =
            results
                .mapNotNull {
                    (it as? TvmTestFailure)?.failure?.exit
                }.filterIsInstance<TvmIntegerOverflowError>()
        assertTrue(exceptions.isNotEmpty(), "Division by zero was not found!")
    }

    @Test
    fun `testing all methods when inlining must throw exception`() {
        assertThrows<NoSelectedMethodsToAnalyze> {
            analyzeAllMethods(code)
        }
    }
}
