package org.ton.examples.cell

import org.ton.TvmInputInfo
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcAnalyzer
import org.ton.test.utils.hasExitCode
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.NoAdditionalStopStrategy
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmConcreteGeneralData
import org.usvm.machine.TvmManualStateProcessor
import org.usvm.machine.TvmOptions
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.analyzeSpecificMethod
import org.usvm.machine.interpreter.TvmValueFixator
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.readCellRef
import org.usvm.machine.state.readSliceCell
import org.usvm.machine.toMethodId
import org.usvm.mkSizeExpr
import org.usvm.test.resolver.TvmTestStateResolver
import kotlin.test.Test

class NoExceedingFixationInSha256 {
    private val noExceedingFixationTest = "/cell/postprocess/sha256-moderate-fixation.fc"

    @Test
    fun test() {
        val contract =
            funcAnalyzer.convertToTvmContractCode(extractResource(noExceedingFixationTest))
        val stateProcessor =
            object : TvmManualStateProcessor() {
                override fun postProcessAfterPartialConcretization(state: TvmState): List<TvmState> {
                    val relevantRef = state.refToSha256.toList().singleOrNull() ?: return emptyList()

                    val sha256edSlice = state.ctx.mkConcreteHeapRef(relevantRef.first)
                    val cell = state.readSliceCell(sha256edSlice)
                    val cellChild = state.readCellRef(cell, state.ctx.mkSizeExpr(0))

                    val scope = TvmStepScopeManager(state, UForkBlackList.createDefault(), false)
                    val resolver = TvmTestStateResolver(state.ctx, state.tvmModels.first(), state)
                    val fixator = TvmValueFixator(resolver, state.ctx, false)
                    val hasOnlyASingleValue =
                        fixator.fixateConcreteValue(scope, cellChild)
                            ?: return listOf()
                    scope.assert(with(state.ctx) { hasOnlyASingleValue.not() })
                        ?: return listOf()
                    return super.postProcessAfterPartialConcretization(state)
                }
            }
        val tests =
            analyzeSpecificMethod(
                contract,
                0.toMethodId(),
                TvmConcreteGeneralData(),
                TvmConcreteContractData(),
                TvmInputInfo(emptyMap()),
                TvmOptions(),
                additionalStopStrategy = NoAdditionalStopStrategy,
                manualStateProcessor = stateProcessor,
            )
        tests.assertPropertiesFound(hasExitCode(0))
    }
}
