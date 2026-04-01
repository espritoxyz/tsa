package org.ton.examples.hash

import org.ton.test.gen.dsl.render.TsRenderer.ContractType
import org.ton.test.utils.TvmTestExecutor
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TlbOptions
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Ignore
import kotlin.test.Test

class HashEqualityTest {
    private val hashEqPath = "/hash/hash_eq.fc"
    private val hashEqConcretePath = "/hash/hash_eq_concrete.fc"

    @Ignore("Current implementation of hashes doesn't solve such constraints")
    @Test
    fun testHashEquality() {
        val path = extractResource(hashEqPath)

        val tests =
            funcCompileAndAnalyzeAllMethods(
                path,
                tvmOptions =
                    TvmOptions(
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions =
                            TlbOptions(
                                performTlbChecksOnAllocatedCells = true,
                            ),
                    ),
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
    fun testHashEqualityConcrete() {
        val path = extractResource(hashEqConcretePath)

        val tests =
            funcCompileAndAnalyzeAllMethods(
                path,
                tvmOptions =
                    TvmOptions(
                        performAdditionalChecksWhileResolving = true,
                        tlbOptions =
                            TlbOptions(
                                performTlbChecksOnAllocatedCells = true,
                            ),
                    ),
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
}
