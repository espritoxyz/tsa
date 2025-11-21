package org.usvm.machine.state

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.ton.bytecode.TvmCodeBlock
import org.ton.bytecode.TvmDisasmCodeBlock
import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmRealInst
import org.ton.targets.TvmTarget
import org.usvm.PathNode
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UCallStack
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UState
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.isStaticHeapRef
import org.usvm.machine.TvmContext
import org.usvm.machine.fields.TvmFieldManagers
import org.usvm.machine.interpreter.inputdict.InputDictionaryStorage
import org.usvm.machine.state.TvmStack.TvmStackTupleValueConcreteNew
import org.usvm.machine.state.input.ReceiverInput
import org.usvm.machine.state.input.TvmInput
import org.usvm.machine.state.messages.FwdFeeInfo
import org.usvm.machine.state.messages.MessageActionParseResult
import org.usvm.machine.state.messages.ReceivedMessage
import org.usvm.machine.types.TvmCellDataTypeRead
import org.usvm.machine.types.TvmDataCellInfoStorage
import org.usvm.machine.types.TvmDataCellLoadedTypeInfo
import org.usvm.machine.types.TvmRealReferenceType
import org.usvm.machine.types.TvmStructuralConstraintsHolder
import org.usvm.machine.types.TvmType
import org.usvm.machine.types.TvmTypeSystem
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet

typealias ContractId = Int

fun <T> PathNode<T>.statementOrNull() = if (this == PathNode.root<T>()) null else statement

class TvmState(
    ctx: TvmContext,
    ownership: MutabilityOwnership,
    override val entrypoint: TvmDisasmCodeBlock,
    val emptyRefValue: TvmRefEmptyValue,
    private var symbolicRefs: PersistentSet<UConcreteHeapAddress> = persistentHashSetOf(),
    var gasUsageHistory: PersistentList<Pair<ContractId, UExpr<UBv32Sort>>>,
    callStack: UCallStack<TvmCodeBlock, TvmInst> = UCallStack(),
    pathConstraints: UPathConstraints<TvmType>,
    memory: UMemory<TvmType, TvmCodeBlock>,
    models: List<UModelBase<TvmType>> = listOf(),
    pathNode: PathNode<TvmInst> = PathNode.root(),
    forkPoints: PathNode<PathNode<TvmInst>> = PathNode.root(),
    var phase: TvmPhase = TvmComputePhase,
    var result: TvmResult = TvmResult.NoCall,
    targets: UTargetsSet<TvmTarget, TvmInst> = UTargetsSet.empty(),
    val typeSystem: TvmTypeSystem,
    var lastCommitedStateOfContracts: PersistentMap<ContractId, TvmCommitedState> = persistentMapOf(),
    val dataCellLoadedTypeInfo: TvmDataCellLoadedTypeInfo = TvmDataCellLoadedTypeInfo.empty(),
    var stateInitialized: Boolean = false,
    var structuralConstraintsHolder: TvmStructuralConstraintsHolder = TvmStructuralConstraintsHolder(),
    val fieldManagers: TvmFieldManagers = TvmFieldManagers(ctx),
    var allowFailures: Boolean = true, // new value starts being active only from the next step
    var contractStack: PersistentList<TvmEventInformation> = persistentListOf(),
    var currentContract: ContractId,
    var fetchedValues: PersistentMap<Int, TvmStack.TvmStackEntry> = persistentMapOf(),
    var additionalFlags: PersistentSet<String> = persistentHashSetOf(),
    var unprocessedMessages: PersistentList<Pair<ContractId, MessageActionParseResult>> = persistentListOf(),
    // inter-contract fields
    var messageQueue: PersistentList<ReceivedMessage.MessageFromOtherContract> = persistentListOf(),
    var intercontractPath: PersistentList<ContractId> = persistentListOf(),
    // post-process fields
    var refToHash: PersistentMap<UConcreteHeapAddress, UExpr<TvmContext.TvmInt257Sort>> = persistentMapOf(),
    var refToDepth: PersistentMap<UConcreteHeapAddress, UExpr<TvmContext.TvmInt257Sort>> = persistentMapOf(),
    var forwardFees: PersistentSet<FwdFeeInfo> = persistentSetOf(),
    var signatureChecks: PersistentList<TvmSignatureCheck> = persistentListOf(),
    var additionalInputs: PersistentMap<Int, ReceiverInput> = persistentMapOf(),
    var acceptedInputs: PersistentSet<ReceiverInput> = persistentSetOf(),
    var receivedMessage: ReceivedMessage? = null,
    var eventsLog: PersistentList<TvmMessageDrivenContractExecutionEntry> = persistentListOf(),
    var currentPhaseBeginTime: Int = 0,
    var currentPhaseEndTime: Int? = null,
    var currentComputeFeeUsed: UExpr<TvmContext.TvmInt257Sort>? = null,
    val debugInfo: TvmStateDebugInfo = TvmStateDebugInfo(),
    var inputDictionaryStorage: InputDictionaryStorage = InputDictionaryStorage(),
) : UState<TvmType, TvmCodeBlock, TvmInst, TvmContext, TvmTarget, TvmState>(
        ctx,
        ownership,
        callStack,
        pathConstraints,
        memory,
        models,
        pathNode,
        forkPoints,
        targets,
    ) {
    val pseudologicalTime: Int
        get() = gasUsageHistory.size
    val currentEventId: EventId
        get() = currentPhaseBeginTime

    override var isExceptional: Boolean = false

    val isTerminated: Boolean
        get() = phase == TvmTerminated

    lateinit var dataCellInfoStorage: TvmDataCellInfoStorage
    lateinit var registersOfCurrentContract: TvmRegisters
    lateinit var contractIdToC4Register: PersistentMap<ContractId, C4Register>
    lateinit var contractIdToFirstElementOfC7: PersistentMap<ContractId, TvmStackTupleValueConcreteNew>

    /**
     * We preserve the invariant that there is a single checker contract and it has a single
     * global state. For this reason, we preserve c7 registers so we can restore the global variables
     * between invocations of / returns to the handler
     */
    var checkerC7: C7Register? = null
    lateinit var contractIdToInitialData: Map<ContractId, TvmInitialStateData>
    lateinit var stack: TvmStack
    lateinit var initialInput: TvmInput

    val contractIds: Set<ContractId>
        get() = contractIdToInitialData.keys

    val rootStack: TvmStack
        get() = if (contractStack.isEmpty()) stack else contractStack.first().executionMemory.stack

    val rootContractId: ContractId
        get() = if (contractStack.isEmpty()) currentContract else contractStack.first().contractId

    val rootInitialData: TvmInitialStateData
        get() {
            return contractIdToInitialData[rootContractId]
                ?: error("Initial data of contract $rootContractId not found")
        }

    val lastRealStmt: TvmRealInst?
        get() {
            var node: PathNode<*>? = pathNode
            while (node?.statementOrNull() !is TvmRealInst?) {
                node = node.parent
            }
            return (node?.statementOrNull() as? TvmRealInst)
        }

    override fun clone(newConstraints: UPathConstraints<TvmType>?): TvmState {
        val newThisOwnership = MutabilityOwnership()
        val cloneOwnership = MutabilityOwnership()
        val newPathConstraints =
            newConstraints?.also {
                this.pathConstraints.changeOwnership(newThisOwnership)
                it.changeOwnership(cloneOwnership)
            } ?: pathConstraints.clone(newThisOwnership, cloneOwnership)
        val newMemory = memory.clone(newPathConstraints.typeConstraints, newThisOwnership, cloneOwnership)

        return TvmState(
            ctx = ctx,
            ownership = ownership,
            entrypoint = entrypoint,
            emptyRefValue = emptyRefValue,
            symbolicRefs = symbolicRefs,
            gasUsageHistory = gasUsageHistory,
            callStack = callStack.clone(),
            pathConstraints = newPathConstraints,
            memory = newMemory,
            models = models,
            pathNode = pathNode,
            forkPoints = forkPoints,
            result = result,
            targets = targets.clone(),
            typeSystem = typeSystem,
            lastCommitedStateOfContracts = lastCommitedStateOfContracts,
            dataCellLoadedTypeInfo = dataCellLoadedTypeInfo.clone(),
            stateInitialized = stateInitialized,
            structuralConstraintsHolder = structuralConstraintsHolder,
            allowFailures = allowFailures,
            contractStack = contractStack,
            currentContract = currentContract,
            refToHash = refToHash,
            refToDepth = refToDepth,
            signatureChecks = signatureChecks,
            fetchedValues = fetchedValues,
            additionalFlags = additionalFlags,
            fieldManagers = fieldManagers.clone(),
            messageQueue = messageQueue,
            intercontractPath = intercontractPath,
            phase = phase,
            unprocessedMessages = unprocessedMessages,
            additionalInputs = additionalInputs,
            acceptedInputs = acceptedInputs,
            receivedMessage = receivedMessage,
            eventsLog = eventsLog,
            currentPhaseBeginTime = currentPhaseBeginTime,
            debugInfo = debugInfo.clone(),
            inputDictionaryStorage = inputDictionaryStorage,
            forwardFees = forwardFees,
            currentComputeFeeUsed = currentComputeFeeUsed,
            currentPhaseEndTime = currentPhaseEndTime,
        ).also { newState ->
            newState.dataCellInfoStorage = dataCellInfoStorage.clone()
            newState.contractIdToInitialData = contractIdToInitialData
            newState.contractIdToFirstElementOfC7 = contractIdToFirstElementOfC7
            newState.checkerC7 = checkerC7
            newState.registersOfCurrentContract = registersOfCurrentContract.clone()
            newState.contractIdToC4Register = contractIdToC4Register
            newState.stack = stack.clone()
            newState.initialInput = initialInput
            newState.isExceptional = isExceptional
        }
    }

    override fun toString(): String =
        buildString {
            appendLine("Instruction: $lastStmt")
            if (isExceptional) appendLine("Exception: $result")
        }

    fun generateSymbolicRef(referenceType: TvmRealReferenceType): UConcreteHeapRef =
        memory.allocStatic(referenceType).also { symbolicRefs = symbolicRefs.add(it.address) }

    fun ensureSymbolicRefInitialized(
        ref: UHeapRef,
        referenceType: TvmRealReferenceType,
        initializer: TvmState.(UConcreteHeapRef) -> Unit = {},
    ) {
        if (!isStaticHeapRef(ref)) return

        val refs = symbolicRefs.add(ref.address)
        if (refs === symbolicRefs) return

        symbolicRefs = refs
        memory.types.allocate(ref.address, referenceType)
        initializer(ref)
    }
}

data class TvmCommitedState(
    val c4: C4Register,
    val c5: C5Register,
)

/**
 * Represents a state of an event (for the definition see [TvmMessageDrivenContractExecutionEntry]).
 * @param inst is the first instruction to be executed when we pop the `TvmContractPosition` from the stack
 * @param stackEntriesToTake number of entries to fetch from the upper contract
 * (from the point of [TvmState.contractStack]) when it exited
 */
data class TvmEventInformation(
    val contractId: ContractId,
    val inst: TvmInst,
    val executionMemory: TvmContractExecutionMemory,
    val stackEntriesToTake: Int,
    val phaseBeginTime: Int,
    val phaseEndTime: Int?,
    val receivedMessage: ReceivedMessage?,
    val isExceptional: Boolean,
    val computeFee: UExpr<TvmContext.TvmInt257Sort>?,
    val phase: TvmPhase,
)

data class TvmContractExecutionMemory(
    val stack: TvmStack,
    val registers: TvmRegisters,
)

data class MemoryAccessInformation(
    val inst: TvmInst,
    val pseudoLogicalTime: Int,
    val type: TvmCellDataTypeRead<*>,
)

class TvmStateDebugInfo(
    var numberOfDataEqualityConstraintsFromTlb: Int = 0,
    var dataConstraints: PersistentSet<UBoolExpr> = persistentSetOf(),
    var extractedTlbGrams: PersistentSet<UExpr<TvmContext.TvmInt257Sort>> = persistentSetOf(),
    var tlbMemoryHits: PersistentList<MemoryAccessInformation> = persistentListOf(),
    var tlbMemoryMisses: PersistentList<MemoryAccessInformation> = persistentListOf(),
) {
    fun clone() =
        TvmStateDebugInfo(
            numberOfDataEqualityConstraintsFromTlb,
            dataConstraints,
            extractedTlbGrams,
            tlbMemoryHits,
            tlbMemoryMisses,
        )
}
