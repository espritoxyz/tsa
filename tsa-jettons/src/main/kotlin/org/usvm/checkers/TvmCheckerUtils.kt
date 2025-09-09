package org.usvm.checkers

import org.ton.TvmInputInfo
import org.ton.bytecode.MethodId
import org.ton.bytecode.TsaContractCode
import org.usvm.FirstFailureTerminator
import org.usvm.machine.TvmManualStateProcessor
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.state.TvmState
import org.usvm.stopstrategies.StopStrategy
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSymbolicTest

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
    val additionalStopStrategy = FirstFailureTerminator()
    val analysisResult =
        analyzeInterContract(
            contracts,
            startContractId = 0,
            methodId = MethodId.ZERO,
            additionalStopStrategy =
                if (stopWhenFoundOneConflictingExecution) {
                    additionalStopStrategy
                } else {
                    StopStrategy {
                        false
                    }
                },
            additionalObserver = if (stopWhenFoundOneConflictingExecution) additionalStopStrategy else null,
            options =
                TvmOptions(
                    turnOnTLBParsingChecks = false,
                    useReceiverInputs = useRecvInternalInput
                ),
            inputInfo = inputInfo ?: TvmInputInfo(),
            manualStateProcessor = postProcessor,
            throwNotImplementedError = true
        )
    val foundTests = analysisResult.tests
    val result = foundTests.filter { it.result is TvmMethodFailure }
    return result
}
