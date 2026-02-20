package org.ton.examples.cell

import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.utils.TvmTestExecutor
import org.ton.test.utils.compareSymbolicAndConcreteFromResource
import org.ton.test.utils.compareSymbolicAndConcreteResultsFunc
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Test

class CellBuildTest {
    private val storeSliceConstFif: String = "/cell/cell-build/CellBuild.fif"
    private val storeOnesFunc: String = "/cell/cell-build/StonesSymbolicSize.fc"
    private val sdpfxFunc: String = "/cell/sdpfx.fc"
    private val sdpfxrevFunc: String = "/cell/sdpfxrev.fc"

    /**
     * pp = proper prefix
     */
    private val sdppfxFunc: String = "/cell/sdppfx.fc"
    private val sdppfxrevFunc: String = "/cell/sdppfxrev.fc"

    @Test
    fun cellBuildTest() {
        compareSymbolicAndConcreteFromResource(testPath = storeSliceConstFif, lastMethodIndex = 15)
    }

    @Test
    fun cellStonesSymbolicTest() {
        val path = extractResource(storeOnesFunc)
        val result = funcCompileAndAnalyzeAllMethods(path)

        propertiesFound(
            result.testSuites.single(),
            listOf(
                { test -> (test.result as? TvmTestFailure)?.exitCode == 129 },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 130 },
            ),
        )
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun `test prefix functions`() {
        compareSymbolicAndConcreteResultsFunc(sdpfxFunc, (0..4).toSet())
    }

    @Test
    fun `test prefix rev functions`() {
        compareSymbolicAndConcreteResultsFunc(sdpfxrevFunc, (0..4).toSet())
    }

    @Test
    fun `test proper prefix functions`() {
        compareSymbolicAndConcreteResultsFunc(sdppfxFunc, (0..4).toSet())
    }

    @Test
    fun `test proper prefix rev functions`() {
        compareSymbolicAndConcreteResultsFunc(sdppfxrevFunc, (0..4).toSet())
    }
}
