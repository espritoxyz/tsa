package org.usvm.machine

import org.ton.bytecode.MethodId
import org.ton.bytecode.TsaContractCode
import org.usvm.PathSelectionStrategy
import org.usvm.UExpr
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmTerminated
import org.usvm.machine.state.generateSymbolicSlice
import org.usvm.machine.state.input.RecvInternalInput
import org.usvm.machine.state.killCurrentState
import org.usvm.machine.state.sliceLoadIntTlb
import java.math.BigInteger
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val DEFAULT_OPCODE_EXTRACTION_TIMEOUT_SECONDS = 20

class TvmOpcodeExtractor(
    val opcodeLength: Int = 32,
) {
    private val random = Random(0)
    private val randomOpcode = random.nextLong(0L, 4294967296L)

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
                    interestingExitCodes = emptySet(),
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
                checkSat(value eq randomOpcode.toBv257())
                    ?: run {
                        // get here if [value == random opcode] couldn't be satisfied (maybe due to unknown)

                        opcodes += listPossibleOpcodes(this, value)

                        return@sliceLoadIntTlb
                    }

                // to skip postprocess phase
                killCurrentState()
            }

            return opcodes
        }

    private fun listPossibleOpcodes(
        scope: TvmStepScopeManager,
        value: UExpr<TvmContext.TvmInt257Sort>,
        limit: Int = 10,
    ): List<BigInteger> =
        with(scope.ctx) {
            if (limit <= 0) {
                return emptyList()
            }
            val concreteValue =
                scope.calcOnState {
                    models.first().eval(value)
                }

            scope.assert(value neq concreteValue)
                ?: return listOf(concreteValue.bigIntValue())

            return listPossibleOpcodes(scope, value, limit - 1) + concreteValue.bigIntValue()
        }
}
