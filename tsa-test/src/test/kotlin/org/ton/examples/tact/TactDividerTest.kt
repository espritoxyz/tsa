package org.ton.examples.tact

import org.ton.bytecode.MethodId
import org.ton.test.utils.tactCompileAndAnalyzeAllMethods
import org.usvm.machine.TactSourcesDescription
import org.usvm.machine.getResourcePath
import org.usvm.machine.state.TvmIntegerOverflowError
import org.usvm.test.resolver.TvmMethodFailure
import kotlin.test.Test
import kotlin.test.assertTrue

class TactDividerTest {
    private val tactConfigPath: String = "/tact/tact.config.json"

    @Test
    fun testDivider() {
        val resourcePath = getResourcePath<TactDividerTest>(tactConfigPath)

        val symbolicResult = tactCompileAndAnalyzeAllMethods(
            TactSourcesDescription(resourcePath, "Divider", "Divider"),
            methodWhiteList = setOf(MethodId.valueOf(95202L)),
        )

        val allTests = symbolicResult.map { it.tests }.flatten()
        val results = allTests.map { it.result }
        val exceptions = results.mapNotNull { (it as? TvmMethodFailure)?.failure?.exit }.filterIsInstance<TvmIntegerOverflowError>()
        assertTrue(exceptions.isNotEmpty(), "Division by zero was not found!")
    }
}
