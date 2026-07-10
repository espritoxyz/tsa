package org.ton.examples.hash

import org.ton.test.gen.dsl.render.TsRenderer.ContractType
import org.ton.test.utils.TvmTestExecutor
import org.ton.test.utils.assertInvariantHolds
import org.ton.test.utils.assertInvariantHoldsOrEmpty
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.doesNotEndWithExitCode
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.hasExitCode
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TlbOptions
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Test

class HashEqualityTest {
    private val hashEqPath = "/hash/hash_eq.fc"
    private val hashEqDictsPath = "/hash/hash_eq_with_dict_dict.fc"
    private val hashEqDictCellPath = "/hash/hash_eq_with_dict_cell.fc"
    private val hashEqConcretePath = "/hash/hash_eq_concrete.fc"

    private val drainWithStateInitChecker = "/hash/drain-check/drain_checker_stateinit.fc"
    private val vulnerableContract = "/hash/drain-check/vulnerable.fc"

    private val tvmOptions =
        TvmOptions(
            performAdditionalChecksWhileResolving = true,
            tlbOptions = TlbOptions(performTlbChecksOnAllocatedCells = true),
        )

    @Test
    fun testHashEquality() {
        val path = extractResource(hashEqPath)
        val tests =
            funcCompileAndAnalyzeAllMethods(
                path,
                tvmOptions = this.tvmOptions,
                methodWhiteList = setOf(TvmContext.RECEIVE_INTERNAL_ID),
            ).single()

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmTestFailure)?.exitCode == 111 },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 112 },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 116 },
            ),
        )
    }

    @Test
    fun `test hash equality with dicts`() {
        val path = extractResource(hashEqDictsPath)
        val tests =
            funcCompileAndAnalyzeAllMethods(
                path,
                tvmOptions = tvmOptions,
                methodWhiteList = setOf(TvmContext.RECEIVE_INTERNAL_ID),
            ).single()

        tests.assertPropertiesFound(hasExitCode(111))
        tests.assertInvariantHolds(doesNotEndWithExitCode(112))
    }

    @Test
    fun `test hash equality with dict and cell `() {
        val path = extractResource(hashEqDictCellPath)
        val tests =
            funcCompileAndAnalyzeAllMethods(
                path,
                tvmOptions = tvmOptions,
                methodWhiteList = setOf(TvmContext.RECEIVE_INTERNAL_ID),
            ).single()

        // the support for dicts is very partial, so we cannot be sure yet whether we found the expected execution
        tests.assertInvariantHoldsOrEmpty(doesNotEndWithExitCode(111))
    }

    @Test
    fun testHashEqualityConcrete() {
        val path = extractResource(hashEqConcretePath)

        val tests =
            funcCompileAndAnalyzeAllMethods(
                path,
                tvmOptions =
                tvmOptions,
                methodWhiteList = setOf(TvmContext.RECEIVE_INTERNAL_ID),
            ).single()

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmTestFailure)?.exitCode == 111 },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 112 },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 116 },
            ),
        )

        TvmTestExecutor.executeGeneratedTests(tests, path, ContractType.Func)
    }

    @Test
    fun testDrainWithStateInit() {
        val checker = extractCheckerContractFromResource(drainWithStateInitChecker)
        val contract = extractFuncContractFromResource(vulnerableContract)

        val options =
            TvmOptions(
                stopOnFirstError = false,
                enableOutMessageAnalysis = true,
            )

        val tests =
            analyzeInterContract(
                listOf(checker, contract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        propertiesFound(
            tests,
            listOf(
                { test -> (test.result as? TvmTestFailure)?.exitCode == 500 },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 1000 },
            ),
        )
    }
}
