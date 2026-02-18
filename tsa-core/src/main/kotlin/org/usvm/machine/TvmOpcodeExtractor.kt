package org.usvm.machine

import org.ton.bytecode.MethodId
import org.ton.bytecode.TsaContractCode
import org.usvm.PathSelectionStrategy
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmTerminated
import org.usvm.machine.state.generateSymbolicSlice
import org.usvm.machine.state.input.RecvInternalInput
import org.usvm.machine.state.killCurrentState
import org.usvm.machine.state.sliceLoadIntTlb
import java.math.BigInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val DEFAULT_OPCODE_EXTRACTION_TIMEOUT_SECONDS = 20

class TvmOpcodeExtractor(
    val opcodeLength: Int = 32,
) {
    fun extractOpcodes(
        code: TsaContractCode,
        methodId: MethodId = TvmContext.RECEIVE_INTERNAL_ID,
        timeout: Duration = DEFAULT_OPCODE_EXTRACTION_TIMEOUT_SECONDS.seconds,
    ): Set<BigInteger> {
        val machine =
            TvmMachine(
                tvmOptions =
                    TvmOptions(
                        excludeExecutionsWithFailures = true,
                        quietMode = true,
                        turnOnTLBParsingChecks = false,
                        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
                        timeout = timeout,
                        collectNonTerminatedState = true,
                    ),
            )

        val opcodes = mutableSetOf<BigInteger>()

        machine.use {
            val coverageStatistics = TvmCoverageStatistics(observedContractId = 0, code.mainMethod)

            val states =
                machine.analyze(
                    code,
                    coverageStatistics = coverageStatistics,
                    methodId = methodId,
                    concreteGeneralData = TvmConcreteGeneralData(),
                    concreteContractData = TvmConcreteContractData(),
                    manualStateProcessor = StatePostprocessor(opcodes, this),
                )

            states.forEach { state ->
                // terminated states were already processed by manual state processor
                if (state.phase != TvmTerminated) {
                    opcodes += extractOpcodeFromState(state)
                }
            }
        }

        return opcodes
    }

    private class StatePostprocessor(
        val result: MutableSet<BigInteger>,
        val extractor: TvmOpcodeExtractor,
    ) : TvmManualStateProcessor() {
        override fun postProcessBeforePartialConcretization(state: TvmState): List<TvmState> {
            result += extractor.extractOpcodeFromState(state)
            return emptyList()
        }
    }

    private fun extractOpcodeFromState(state: TvmState): Set<BigInteger> =
        with(state.ctx) {
            val input =
                state.initialInput as? RecvInternalInput
                    ?: error("Unexpected input: ${state.initialInput}")

            // hack
            state.isExceptional = false

            val scope =
                TvmStepScopeManager(
                    state,
                    UForkBlackList.createDefault(),
                    allowFailuresOnCurrentStep = false,
                )

            val opcodes = mutableSetOf<BigInteger>()

            sliceLoadIntTlb(
                scope,
                input.msgBodySliceMaybeBounced,
                state.generateSymbolicSlice(),
                sizeBits = opcodeLength,
                isSigned = false,
            ) { value ->
                val concreteValue = state.models.first().eval(value)

                assert(
                    value neq concreteValue,
                ) ?: run {
                    // get here if [value != concreteValue] couldn't be satisfied (maybe due to unknown)
                    opcodes.add(concreteValue.bigIntValue())
                    return@sliceLoadIntTlb
                }

                // to skip postprocess phase
                killCurrentState()
            }

            return opcodes
        }
}
