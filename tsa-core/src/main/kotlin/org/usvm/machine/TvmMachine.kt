package org.usvm.machine

import mu.KLogging
import org.ton.TvmInputInfo
import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.TvmCodeBlock
import org.usvm.StateCollectionStrategy
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.machine.interpreter.TvmInterpreter
import org.usvm.machine.ps.PSCreationContext
import org.usvm.machine.ps.TvmSeedBasedPathSelector
import org.usvm.machine.ps.createPathSelector
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmState
import org.usvm.machine.statistics.StateHistoryGraph
import org.usvm.machine.statistics.getTvmDebugProfileObserver
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.StepsStatistics
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.collectors.AllStatesCollector
import org.usvm.statistics.collectors.StatesCollector
import org.usvm.stopstrategies.GroupedStopStrategy
import org.usvm.stopstrategies.StepLimitStopStrategy
import org.usvm.stopstrategies.StopStrategy
import org.usvm.stopstrategies.TimeoutStopStrategy
import java.math.BigInteger
import kotlin.time.Duration.Companion.INFINITE

class TvmMachine(
    private val tvmOptions: TvmOptions = TvmOptions(),
) : UMachine<TvmState>() {
    override val options: UMachineOptions =
        defaultOptions.copy(
            timeout = tvmOptions.timeout,
            solverTimeout = tvmOptions.solverTimeout,
        )

    private val components = TvmComponents(tvmOptions)
    private val ctx = TvmContext(tvmOptions, components)

    fun analyze(
        contractCode: TsaContractCode,
        concreteGeneralData: TvmConcreteGeneralData,
        concreteContractData: TvmConcreteContractData,
        coverageStatistics: TvmCoverageStatistics,
        methodId: BigInteger,
        inputInfo: TvmInputInfo = TvmInputInfo(),
        manualStateProcessor: TvmManualStateProcessor = TvmManualStateProcessor(),
        additionalStopStrategy: TvmAdditionalStopStrategy = NoAdditionalStopStrategy,
        interestingExitCodes: Set<Int>,
    ): List<TvmState> =
        analyze(
            listOf(contractCode),
            startContractId = 0,
            concreteGeneralData,
            listOf(concreteContractData),
            coverageStatistics,
            methodId,
            inputInfo,
            manualStateProcessor = manualStateProcessor,
            additionalStopStrategy = additionalStopStrategy,
            interestingExitCodes,
        )

    fun analyze(
        contractsCode: List<TsaContractCode>,
        startContractId: ContractId,
        concreteGeneralData: TvmConcreteGeneralData,
        concreteContractData: List<TvmConcreteContractData>,
        coverageStatistics: TvmCoverageStatistics, // TODO: adapt for several contracts
        methodId: BigInteger,
        inputInfo: TvmInputInfo = TvmInputInfo(),
        manualStateProcessor: TvmManualStateProcessor = TvmManualStateProcessor(),
        additionalStopStrategy: TvmAdditionalStopStrategy = NoAdditionalStopStrategy,
        interestingExitCodes: Set<Int>,
    ): List<TvmState> {
        val interpreter =
            TvmInterpreter(
                ctx,
                contractsCode,
                typeSystem = components.typeSystem,
                inputInfo = inputInfo,
                manualStateProcessor,
                interestingExitCodes = interestingExitCodes,
            )
        logger.debug("{}.analyze({})", this, contractsCode)

        val timeStatistics = TimeStatistics<TvmCodeBlock, TvmState>()

        val psContext =
            PSCreationContext(
                tvmOptions,
                timeStatistics = timeStatistics,
                loopTracker = TvmLoopTracker(),
            )

        val pathSelector =
            createPathSelector(psContext) { opcodeInfo ->
                opcodeInfo?.let {
                    check(tvmOptions.divideTimeBetweenOpcodes != null) {
                        "If opcode info is given, then [divideTimeBetweenOpcodes] should be non-null"
                    }

                    interpreter.getInitialState(
                        startContractId,
                        concreteGeneralData,
                        concreteContractData,
                        methodId,
                        additionalInputsConcreteData =
                            mapOf(
                                tvmOptions.divideTimeBetweenOpcodes.inputId to
                                    MessageConcreteData(opcodeInfo = it),
                            ),
                    )
                } ?: let {
                    interpreter.getInitialState(
                        startContractId,
                        concreteGeneralData,
                        concreteContractData,
                        methodId,
                    )
                }
            }

        val stepLimit = options.stepLimit
        val stepsStatistics = StepsStatistics<TvmCodeBlock, TvmState>()
        val stopStrategy =
            if (stepLimit != null) {
                StepLimitStopStrategy(stepLimit, stepsStatistics)
            } else {
                StopStrategy { false }
            }

        val timeoutStopStrategy =
            if (!tvmOptions.addTimeoutIfNotSatiated) {
                TimeoutStopStrategy(options.timeout, timeStatistics)
            } else {
                check(pathSelector is TvmSeedBasedPathSelector) {
                    "Can add timeout only with [TvmShakerPathSelector]"
                }
                pathSelector
            }

        val integrativeStopStrategy =
            GroupedStopStrategy(listOf(stopStrategy, additionalStopStrategy, timeoutStopStrategy))

        val statesCollector =
            if (!tvmOptions.collectNonTerminatedState) {
                AllStatesCollector()
            } else {
                object : StatesCollector<TvmState> {
                    override val collectedStates: List<TvmState>
                        get() = collectedStatesSet.toList()

                    private val collectedStatesSet = mutableSetOf<TvmState>()

                    override fun onState(
                        parent: TvmState,
                        forks: Sequence<TvmState>,
                    ) {
                        collectedStatesSet.add(parent)
                        forks.forEach {
                            collectedStatesSet.add(it)
                        }
                    }
                }
            }

        val observers =
            mutableListOf(
                statesCollector,
                stepsStatistics,
                coverageStatistics,
                timeStatistics,
                additionalStopStrategy,
            )

        val psObserver = psContext.psObserver
        if (psObserver != null) {
            observers.add(psObserver)
        }

        if (logger.isDebugEnabled && contractsCode.size == 1) {
            val code = contractsCode.single()
            val profiler = getTvmDebugProfileObserver(code)
            observers.add(profiler)
        }

        if (logger.isDebugEnabled) {
            observers.add(StateHistoryGraph())
        }

        run(
            interpreter,
            pathSelector,
            observer = CompositeUMachineObserver(observers),
            isStateTerminated = ::isStateTerminated,
            stopStrategy = integrativeStopStrategy,
        )

        return statesCollector.collectedStates
    }

    private fun isStateTerminated(state: TvmState): Boolean = state.isTerminated

    companion object {
        private val logger = object : KLogging() {}.logger

        const val DEFAULT_MAX_RECURSION_DEPTH: Int = 2
        const val DEFAULT_LOOP_ITERATIONS_LIMIT: Int = 2 // TODO find the best value
        const val DEFAULT_MAX_TLB_DEPTH = 10
        const val DEFAULT_MAX_CELL_DEPTH_FOR_DEFAULT_CELLS_CONSISTENT_WITH_TLB = 10

        val defaultOptions: UMachineOptions =
            UMachineOptions(
                stateCollectionStrategy = StateCollectionStrategy.ALL,
                timeout = INFINITE,
                stopOnCoverage = -1,
                stepLimit = null,
                throwExceptionOnStepFailure = true,
            )
    }

    override fun close() {
        components.close()
    }
}
