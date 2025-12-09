package org.ton.examples.cell

import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.utils.TvmTestExecutor
import org.ton.test.utils.compareSymbolicAndConcreteFromResource
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Test

class CellBuildTest {
    private val storeSliceConstFif: String = "/cell/cell-build/CellBuild.fif"
    private val storeOnesFunc: String = "/cell/cell-build/StonesSymbolicSize.fc"

    @Test
    fun cellBuildTest() {
        compareSymbolicAndConcreteFromResource(testPath = storeSliceConstFif, lastMethodIndex = 13)
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
}
