package org.ton.examples.dict

import org.ton.boc.BagOfCells
import org.ton.bytecode.MethodId
import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.utils.TvmTestExecutor
import org.ton.test.utils.compareSymbolicAndConcreteResultsFunc
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.usvm.machine.BocAnalyzer
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmOptions
import org.usvm.machine.getResourcePath
import org.usvm.test.resolver.TvmMethodFailure
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DictComplexTest {
    private val nearestPath: String = "/dict/nearest.fc"
    private val minmaxPath: String = "/dict/minmax.fc"

    private val bigConcreteDictCode: String = "/contracts/EQCYU8pXneDdrKrD7KF_arCs-pbl9zzsdg33c0T5akHOnEd-.boc"
    private val bigConcreteDictData: String = "/contracts/EQCYU8pXneDdrKrD7KF_arCs-pbl9zzsdg33c0T5akHOnEd-_data.boc"

    private val veryBigConcreteDictCode: String = "/contracts/EQAebdctnt2DE6nSS3dkFHmdxHa4ml_Y8U_SShBYdGCYqj_9.boc"
    private val veryBigConcreteDictData: String = "/contracts/EQAebdctnt2DE6nSS3dkFHmdxHa4ml_Y8U_SShBYdGCYqj_9_data.boc"

    private val potentialDictOverflow = "/dict/value_overflow.fc"
    private val dictFixation = "/dict/dict_fixation.fc"

    @Test
    fun nearestTest() {
        compareSymbolicAndConcreteResultsFunc(nearestPath, methods = setOf(0, 1))
    }

    @Test
    fun minmaxTest() {
        compareSymbolicAndConcreteResultsFunc(minmaxPath, methods = setOf(0))
    }

    @Test
    fun testBigConcreteDict() {
        // 5k elements in concrete dict
        runTestWithBigConcreteDict(bigConcreteDictCode, bigConcreteDictData)
    }

    @Test
    fun testVeryBigConcreteDict() {
        // around 24k elements in concrete dict, and C4 contains much more data
        runTestWithBigConcreteDict(veryBigConcreteDictCode, veryBigConcreteDictData)
    }

    private fun runTestWithBigConcreteDict(
        codePath: String,
        dataPath: String,
    ) {
        val resourcePath = getResourcePath<DictComplexTest>(codePath)
        val dataResourcePath = getResourcePath<DictComplexTest>(dataPath)
        val data = dataResourcePath.toFile().readBytes()
        val parsedData = BagOfCells(data).roots.single()

        val tests =
            BocAnalyzer.analyzeSpecificMethod(
                resourcePath,
                methodId = MethodId.ZERO,
                concreteContractData = TvmConcreteContractData(contractC4 = parsedData),
                tvmOptions =
                    TvmOptions(
                        quietMode = false,
                        timeout = 30.seconds,
                    ),
            )

        assertTrue { tests.isNotEmpty() }
    }

    @Ignore("TODO: fix min/max/next/prev for input dicts")
    @Test
    fun testDictValueOverflow() {
        val path = extractResource(potentialDictOverflow)
        val results = funcCompileAndAnalyzeAllMethods(path)

        assertEquals(1, results.size)

        val tests = results.single()

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 999 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1000 },
            ),
        )

        TvmTestExecutor.executeGeneratedTests(results, path, TsRenderer.ContractType.Func)
    }

    @Ignore("TODO: fix min/max/next/prev for input dicts")
    @Test
    fun testDictFixation() {
        val path = extractResource(dictFixation)
        val results = funcCompileAndAnalyzeAllMethods(path)

        assertEquals(1, results.size)

        val tests = results.single()

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 999 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1000 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1001 },
                { test -> (test.result as? TvmMethodFailure)?.exitCode == 1002 },
            ),
        )

        TvmTestExecutor.executeGeneratedTests(results, path, TsRenderer.ContractType.Func)
    }
}
