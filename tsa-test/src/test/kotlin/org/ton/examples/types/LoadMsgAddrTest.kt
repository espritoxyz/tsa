package org.ton.examples.types

import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.utils.TvmTestExecutor
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TvmOptions
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Test
import kotlin.test.assertEquals

class LoadMsgAddrTest {
    private val fiveMsgAddrPath = "/types/load_5_msg_addr.fc"

    @Test
    fun testFiveMsgAddr() {
        // recv_internal loads five addresses from the input message slice.
        // Five std addresses (267 bits each) cannot fit in a single cell (max 1023 bits),
        // so the only way to load five addresses without a cell underflow is to make all of
        // them addr_none (2 bits each). TSA must find such an input, in which case the contract
        // reaches the final throw(999).
        val resourcePath = extractResource(fiveMsgAddrPath)

        val options = TvmOptions(performAdditionalChecksWhileResolving = true, analyzeBouncedMessaged = false)
        val results =
            funcCompileAndAnalyzeAllMethods(
                resourcePath,
                tvmOptions = options,
            )
        assertEquals(1, results.testSuites.size)
        val tests = results.testSuites.first()

        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode == 999 },
        )

        TvmTestExecutor.executeGeneratedTests(tests, resourcePath, TsRenderer.ContractType.Func)
    }
}
