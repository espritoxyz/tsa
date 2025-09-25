package org.usvm.test.resolver

import org.ton.TlbResolvedBuiltinLabel
import org.ton.bytecode.MethodId
import org.ton.bytecode.TvmArtificialInst
import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmMethod
import org.ton.bytecode.TvmRealInst
import org.usvm.machine.interpreter.TvmInterpreter.Companion.logger
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmMethodResult
import org.usvm.machine.state.TvmMethodResult.TvmFailure
import org.usvm.machine.state.TvmState
import org.usvm.machine.tryCatchIf
import org.usvm.machine.types.TvmStructuralExit

data object TvmTestResolver {
    fun resolve(
        method: TvmMethod,
        state: TvmState,
    ): TvmSymbolicTest = resolve(method.id, state)

    fun resolve(
        methodId: MethodId,
        state: TvmState,
    ): TvmSymbolicTest {
        val model = state.models.first()
        val ctx = state.ctx
        val stateResolver =
            TvmTestStateResolver(ctx, model, state, ctx.tvmOptions.performAdditionalChecksWhileResolving)

        val input = stateResolver.resolveInput()
        val fetchedValues = stateResolver.resolveFetchedValues()
        val config = stateResolver.resolveConfig()
        val contractAddress = stateResolver.resolveContractAddress()
        val time = stateResolver.resolveTime()
        val initialData = stateResolver.resolveInitialData()
        val result = stateResolver.resolveResultStack()
        val gasUsage = stateResolver.resolveGasUsage()
        val outMessages = stateResolver.resolveOutMessages()
        val additionalInputs = stateResolver.resolveAdditionalInputs()
        val contractStatesBefore = state.contractIds.associateWith { stateResolver.resolveInitialContractState(it) }
        val numberOfAddressesWithAssertedDataConstraints =
            state.fieldManagers.cellDataFieldManager
                .getCellsWithAssertedCellData()
                .size

        return TvmSymbolicTest(
            methodId = methodId,
            config = config,
            contractAddress = contractAddress,
            initialData = initialData,
            time = time,
            input = input,
            fetchedValues = fetchedValues,
            result = result,
            lastStmt = state.lastRealStmt,
            gasUsage = gasUsage,
            additionalFlags = state.additionalFlags,
            intercontractPath = state.intercontractPath,
            coveredInstructions = collectVisitedInstructions(state),
            outMessages = outMessages,
            rootContract = state.rootContractId,
            contractStatesBefore = contractStatesBefore,
            additionalInputs = additionalInputs,
            debugInfo =
                TvmTestDebugInfo(
                    numberOfAddressesWithAssertedDataConstraints,
                    state.debugInfo.numberOfDataEqualityConstraintsFromTlb,
                ),
            eventsList =
                state.eventsLog.map { entry ->
                    TvmMessageDrivenContractExecutionTestEntry(
                        id = entry.id,
                        executionBegin = entry.executionBegin,
                        executionEnd = entry.executionEnd,
                        contractId = entry.contractId,
                        incomingMessage = stateResolver.resolveReceivedMessage(entry.incomingMessage),
                        methodResult = stateResolver.resolveResultStackImpl(entry.computePhaseResult),
                        gasUsageHistory = stateResolver.resolvePhaseGasUsage(entry.executionBegin, entry.executionEnd),
                        computeFee = entry.computeFee.let { stateResolver.resolveInt257(it) },
                    )
                },
        )
    }

    fun groupTestSuites(
        testSuites: List<TvmSymbolicTestSuite>,
        takeEmptyTests: Boolean = false,
    ): TvmContractSymbolicTestResult =
        TvmContractSymbolicTestResult(
            testSuites.mapNotNull {
                it.takeIf { takeEmptyTests || it.tests.isNotEmpty() }
            },
        )

    fun resolveSingleMethod(
        methodId: MethodId,
        states: List<TvmState>,
        coverage: TvmMethodCoverage,
    ): TvmSymbolicTestSuite {
        val tests =
            states.mapNotNull { state ->
                tryCatchIf(
                    condition = state.ctx.tvmOptions.quietMode,
                    body = { resolve(methodId, state) },
                    exceptionHandler = { exception ->
                        logger.warn(exception) { "Exception is thrown during the resolve of state $state" }
                        null
                    },
                )
            }

        return TvmSymbolicTestSuite(
            methodId,
            coverage,
            tests,
        )
    }

    private fun collectVisitedInstructions(state: TvmState): List<TvmInst> =
        state.pathNode.allStatements
            .filterNot { it is TvmArtificialInst }
            .reversed()
}

data class TvmContractSymbolicTestResult(
    val testSuites: List<TvmSymbolicTestSuite>,
) : List<TvmSymbolicTestSuite> by testSuites

data class TvmSymbolicTestSuite(
    val methodId: MethodId,
    val methodCoverage: TvmMethodCoverage,
    val tests: List<TvmSymbolicTest>,
) : List<TvmSymbolicTest> by tests

data class TvmMethodCoverage(
    val coverage: Float?,
    val transitiveCoverage: Float?,
    val coverageOfMainMethod: Float?,
)

data class TvmSymbolicTest(
    val methodId: MethodId,
    val config: TvmTestDictCellValue,
    val contractAddress: TvmTestDataCellValue,
    val time: TvmTestIntegerValue,
    val rootContract: ContractId,
    val contractStatesBefore: Map<ContractId, TvmContractState>,
//    val contractStatesAfter: Map<ContractId, TvmContractState>,
    val initialData: Map<ContractId, TvmTestCellValue>,
    val input: TvmTestInput,
    val additionalInputs: Map<Int, TvmTestInput>,
    val fetchedValues: Map<Int, TvmTestValue>,
    val result: TvmMethodSymbolicResult,
    val lastStmt: TvmRealInst?, // null if the body is empty
    val gasUsage: Int,
    val additionalFlags: Set<String>,
    val intercontractPath: List<ContractId>,
    val outMessages: List<Pair<ContractId, TvmTestOutMessage>>,
    // a list of the covered instructions in the order they are visited
    val coveredInstructions: List<TvmInst>,
    val eventsList: List<TvmMessageDrivenContractExecutionTestEntry>,
    val debugInfo: TvmTestDebugInfo,
) {
    val initialRootContractState: TvmContractState
        get() =
            contractStatesBefore[rootContract]
                ?: error("Contract state for root contract not found")

    val rootInitialData: TvmTestCellValue
        get() = initialRootContractState.data

    val initialRootContractBalance: TvmTestIntegerValue
        get() = initialRootContractState.balance
}

data class TvmTestDebugInfo(
    val numberOfAddressesWithAssertedDataConstraints: Int,
    val numberOfDataEqualityConstraintsFromTlb: Int = 0,
)

data class TvmContractState(
    val data: TvmTestCellValue,
    val balance: TvmTestIntegerValue,
)

data class TvmTestOutMessage(
    val value: TvmTestIntegerValue,
    val fullMessage: TvmTestCellValue,
    val bodySlice: TvmTestSliceValue,
)

sealed interface TvmMethodSymbolicResult {
    val stack: List<TvmTestValue>
}

sealed interface TvmTerminalMethodSymbolicResult : TvmMethodSymbolicResult {
    val exitCode: Int
}

data class TvmMethodFailure(
    val failure: TvmFailure,
    val lastStmt: TvmInst,
    override val exitCode: Int,
    override val stack: List<TvmTestValue>,
) : TvmTerminalMethodSymbolicResult

data class TvmSuccessfulExecution(
    override val exitCode: Int,
    override val stack: List<TvmTestValue>,
) : TvmTerminalMethodSymbolicResult

data class TvmExecutionWithStructuralError(
    val lastStmt: TvmInst,
    override val stack: List<TvmTestValue>,
    val exit: TvmStructuralExit<TvmTestCellDataTypeRead, TlbResolvedBuiltinLabel>,
) : TvmMethodSymbolicResult

data class TvmExecutionWithSoftFailure(
    val lastStmt: TvmInst,
    override val stack: List<TvmTestValue>,
    val failure: TvmMethodResult.TvmSoftFailure,
) : TvmMethodSymbolicResult
