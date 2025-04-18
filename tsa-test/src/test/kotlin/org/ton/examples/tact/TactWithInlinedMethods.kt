package org.ton.examples.tact

import org.usvm.machine.BocAnalyzer
import org.usvm.machine.analyzeSpecificMethod
import org.usvm.machine.getResourcePath
import org.usvm.machine.state.TvmIntegerOverflowError
import org.usvm.machine.toMethodId
import org.usvm.test.resolver.TvmMethodFailure
import kotlin.test.Test
import kotlin.test.assertTrue

class TactWithInlinedMethods {
    private val pathToBoc = "/contracts/sample_Divider.code.boc"
    private val resourcePath = getResourcePath<TactDividerTest>(pathToBoc)

    @Test
    fun testDivider() {
        val code = BocAnalyzer.loadContractFromBoc(resourcePath)
        val symbolicResult = analyzeSpecificMethod(code, 95202.toMethodId())

        val results = symbolicResult.map { it.result }
        val exceptions = results.mapNotNull { (it as? TvmMethodFailure)?.failure?.exit }.filterIsInstance<TvmIntegerOverflowError>()
        assertTrue(exceptions.isNotEmpty(), "Division by zero was not found!")
    }
}