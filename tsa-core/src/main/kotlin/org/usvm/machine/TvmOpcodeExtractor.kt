package org.usvm.machine

import org.ton.bytecode.MethodId
import org.ton.bytecode.TsaContractCode
import org.usvm.UExpr
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmTerminated
import org.usvm.machine.state.input.RecvInternalInput
import org.usvm.machine.state.killCurrentState
import org.usvm.machine.state.sliceLoadIntTlbNoForkAndNoRegister
import java.math.BigInteger
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val DEFAULT_OPCODE_EXTRACTION_TIMEOUT_SECONDS = 20

class TvmOpcodeExtractor(
    val opcodeLength: Int = 32,
) {
    private val random = Random(0)
    private val randomOpcode = random.nextLong(0L, 1L shl opcodeLength)

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
                        pathSelectionStrategy = TvmPathSelectionStrategy.BFS_BASED,
                        timeout = timeout,
                        collectNonTerminatedState = true,
                        addTimeoutIfNotSatiated = false,
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
                    val (cur, _) = extractOpcodeFromState(state, isEnd = true)
                    opcodes += cur
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
            val (cur, _) = extractor.extractOpcodeFromState(state, isEnd = true)
            result += cur
            return emptyList()
        }

        override fun preprocessStateBeforeStep(state: TvmState): Unit? {
            val (cur, status) = extractor.extractOpcodeFromState(state, isEnd = false)
            result += cur
            return status
        }
    }

    private fun extractOpcodeFromState(
        state: TvmState,
        isEnd: Boolean,
    ): Pair<Set<BigInteger>, Unit?> =
        with(state.ctx) {
            val scope =
                TvmStepScopeManager(
                    state,
                    UForkBlackList.createDefault(),
                    allowFailuresOnCurrentStep = false,
                )

            val opcodes = mutableSetOf<BigInteger>()

            val status =
                loadOpcode(scope) { value ->
                    checkSat(value eq randomOpcode.toBv257())
                        ?: run {
                            // get here if [value == random opcode] couldn't be satisfied (maybe due to unknown)

                            opcodes += listPossibleOpcodes(this, value)

                            return@loadOpcode null
                        }

                    if (isEnd) null else Unit
                }

            return opcodes to status
        }

    private fun loadOpcode(
        scope: TvmStepScopeManager,
        onOpcode: TvmStepScopeManager.(UExpr<TvmContext.TvmInt257Sort>) -> Unit?,
    ): Unit? =
        scope.calcOnState {
            val input =
                initialInput as? RecvInternalInput
                    ?: error("Unexpected input: $initialInput")

            // hack
            val oldIsExceptional = isExceptional
            isExceptional = false

            val (_, value) =
                sliceLoadIntTlbNoForkAndNoRegister(
                    scope,
                    input.msgBodySliceMaybeBounced,
                    sizeBits = opcodeLength,
                    isSigned = false,
                ) ?: return@calcOnState null

            scope.onOpcode(value)
                ?: return@calcOnState null

            scope.calcOnState {
                isExceptional = oldIsExceptional
            }
        }

    private fun listPossibleOpcodes(
        scope: TvmStepScopeManager,
        value: UExpr<TvmContext.TvmInt257Sort>,
        limit: Int = 10,
    ): List<BigInteger> =
        with(scope.ctx) {
            if (limit <= 0) {
                scope.killCurrentState()
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
