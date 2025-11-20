package org.usvm.checkers

import org.ton.TvmInputInfo
import org.ton.bytecode.MethodId
import org.ton.bytecode.TsaContractCode
import org.usvm.FirstFailureTerminator
import org.usvm.machine.NoAdditionalStopStrategy
import org.usvm.machine.TvmManualStateProcessor
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.state.TvmState
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmTestFailure

fun runAnalysisAndExtractFailingExecutions(
    contracts: List<TsaContractCode>,
    stopWhenFoundOneConflictingExecution: Boolean,
    inputInfo: TvmInputInfo?,
    useRecvInternalInput: Boolean = true,
    manualStatePostProcess: (TvmState) -> List<TvmState> = { listOf(it) },
): List<TvmSymbolicTest> {
    val postProcessor =
        object : TvmManualStateProcessor() {
            override fun postProcessBeforePartialConcretization(state: TvmState): List<TvmState> =
                manualStatePostProcess(state)
        }

    val additionalStopStrategy =
        if (stopWhenFoundOneConflictingExecution) {
            FirstFailureTerminator()
        } else {
            NoAdditionalStopStrategy
        }

    val analysisResult =
        analyzeInterContract(
            contracts,
            startContractId = 0,
            methodId = MethodId.ZERO,
            additionalStopStrategy = additionalStopStrategy,
            options =
                TvmOptions(
                    turnOnTLBParsingChecks = false,
                    useReceiverInputs = useRecvInternalInput,
                ),
            inputInfo = inputInfo ?: TvmInputInfo(),
            manualStateProcessor = postProcessor,
        )
    val foundTests = analysisResult.tests
    val result = foundTests.filter { it.result is TvmTestFailure }
    return result
}
