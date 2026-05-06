package org.ton.examples.random

import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.utils.TvmTestExecutor
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.usvm.machine.TvmConcreteGeneralData
import kotlin.test.Test

class RandomTest {
    private val randomFunc: String = "/cell/random.fc"

    /**
     * Sandbox (i.e. `TvmExecutor.executeGeneratedTests`) is used here for proper initialization of c7 register
     */
    @Test
    fun `random instructions`() {
        val path = extractResource(randomFunc)
        val results =
            funcCompileAndAnalyzeAllMethods(
                path,
                concreteGeneralData = TvmConcreteGeneralData(initialSeed = 1337.toBigInteger()),
            )
        assert(results.testSuites.all { it.tests.size == 1 }) // concrete execution
        TvmTestExecutor.executeGeneratedTests(results, path, TsRenderer.ContractType.Func)
    }
}
