package org.usvm.machine.state

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.ton.bytecode.TsaArtificialActionPhaseInst
import org.ton.bytecode.TsaArtificialExitInst
import org.ton.bytecode.TvmCodeBlock
import org.ton.bytecode.TvmDisasmCodeBlock
import org.ton.bytecode.TvmInst
import org.ton.targets.TvmTarget
import org.usvm.PathNode
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
import org.usvm.machine.TvmCellDataFieldManager
import org.usvm.machine.TvmContext
import org.usvm.machine.interpreter.OutMessage
import org.usvm.machine.state.TmvPhase.COMPUTE_PHASE
import org.usvm.machine.state.TmvPhase.TERMINATED
import org.usvm.machine.state.TvmStack.TvmStackTupleValueConcreteNew
import org.usvm.machine.state.input.TvmStateInput
import org.usvm.machine.types.GlobalStructuralConstraintsHolder
import org.usvm.machine.types.TvmDataCellInfoStorage
import org.usvm.machine.types.TvmDataCellLoadedTypeInfo
import org.usvm.machine.types.TvmRealReferenceType
import org.usvm.machine.types.TvmType
import org.usvm.machine.types.TvmTypeSystem
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet

typealias ContractId = Int

class TvmState(
    ctx: TvmContext,
    ownership: MutabilityOwnership,
    override val entrypoint: TvmDisasmCodeBlock,
//    val registers: TvmRegisters, // TODO do we really need keep the registers this way?
    val emptyRefValue: TvmRefEmptyValue,
    val analysisOfGetMethod: Boolean,
    private var symbolicRefs: PersistentSet<UConcreteHeapAddress> = persistentHashSetOf(),
    var gasUsage: PersistentList<UExpr<UBv32Sort>>,
    // TODO codepage
    callStack: UCallStack<TvmCodeBlock, TvmInst> = UCallStack(),
    pathConstraints: UPathConstraints<TvmType>,
    memory: UMemory<TvmType, TvmCodeBlock>,
    models: List<UModelBase<TvmType>> = listOf(),
    pathNode: PathNode<TvmInst> = PathNode.root(),
    forkPoints: PathNode<PathNode<TvmInst>> = PathNode.root(),
    var phase: TmvPhase = COMPUTE_PHASE,
    var methodResult: TvmMethodResult = TvmMethodResult.NoCall,
    targets: UTargetsSet<TvmTarget, TvmInst> = UTargetsSet.empty(),
    val typeSystem: TvmTypeSystem,
    var lastCommitedStateOfContracts: PersistentMap<ContractId, TvmCommitedState> = persistentMapOf(),
    val dataCellLoadedTypeInfo: TvmDataCellLoadedTypeInfo = TvmDataCellLoadedTypeInfo.empty(),
    var stateInitialized: Boolean = false,
    val globalStructuralConstraintsHolder: GlobalStructuralConstraintsHolder = GlobalStructuralConstraintsHolder(),
    val cellDataFieldManager: TvmCellDataFieldManager = TvmCellDataFieldManager(ctx),
    var allowFailures: Boolean = true,  // new value starts being active only from the next step
    var contractStack: PersistentList<TvmContractPosition> = persistentListOf(),
    var currentContract: ContractId,
    var fetchedValues: PersistentMap<Int, TvmStack.TvmStackEntry> = persistentMapOf(),
    var additionalFlags: PersistentSet<String> = persistentHashSetOf(),
    // inter-contract fields
    var messageQueue: PersistentList<Pair<ContractId, OutMessage>> = persistentListOf(),
    var lastMsgBody: UHeapRef? = null,
    var intercontractPath: PersistentList<ContractId> = persistentListOf(),
    // post-process fields
    var addressToHash: PersistentMap<UHeapRef, UExpr<TvmContext.TvmInt257Sort>> = persistentMapOf(),
    var addressToDepth: PersistentMap<UHeapRef, UExpr<TvmContext.TvmInt257Sort>> = persistentMapOf(),
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
    override val isExceptional: Boolean
        get() = stateInitialized && lastStmt.let {
            it is TsaArtificialActionPhaseInst && it.computePhaseResult.isExceptional() ||
            it is TsaArtificialExitInst && it.result.isExceptional()
        }

    val isTerminated: Boolean
        get() = phase == TERMINATED

    lateinit var dataCellInfoStorage: TvmDataCellInfoStorage
    lateinit var registersOfCurrentContract: TvmRegisters
    lateinit var contractIdToC4Register: PersistentMap<ContractId, C4Register>
    lateinit var contractIdToFirstElementOfC7: PersistentMap<ContractId, TvmStackTupleValueConcreteNew>
    lateinit var contractIdToInitialData: Map<ContractId, TvmInitialStateData>
    lateinit var stack: TvmStack
    lateinit var input: TvmStateInput

    val rootStack: TvmStack
        get() = if (contractStack.isEmpty()) stack else contractStack.first().executionMemory.stack

    val rootInitialData: TvmInitialStateData
        get() {
            val contractId = if (contractStack.isEmpty()) currentContract else contractStack.first().contractId
            return contractIdToInitialData[contractId]
                ?: error("Initial data of contract $contractId not found")
        }

    /**
     * All visited last instructions in all visited continuations in the LIFO order.
     */
    val continuationStack: List<TvmInst>
        get() {
            val instructions = mutableListOf<TvmInst>()
            val allInstructions = pathNode.allStatements.reversed()

            var prevInst: TvmInst? = null
            for (inst in allInstructions) {
                val curBlock = inst.location.codeBlock
                if (prevInst == null) {
                    prevInst = inst
                    continue
                }

                val prevBlock = prevInst.location.codeBlock
                if (prevBlock == curBlock) {
                    prevInst = inst
                    continue
                }

                instructions += prevInst
                prevInst = inst
            }

            instructions += allInstructions.last()

            return instructions.asReversed()
        }

    override fun clone(newConstraints: UPathConstraints<TvmType>?): TvmState {
        val newThisOwnership = MutabilityOwnership()
        val cloneOwnership = MutabilityOwnership()
        val newPathConstraints = newConstraints?.also {
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
            gasUsage = gasUsage,
            callStack = callStack.clone(),
            pathConstraints = newPathConstraints,
            memory = newMemory,
            models = models,
            pathNode = pathNode,
            forkPoints = forkPoints,
            methodResult = methodResult,
            targets = targets.clone(),
            typeSystem = typeSystem,
            lastCommitedStateOfContracts = lastCommitedStateOfContracts,
            dataCellLoadedTypeInfo = dataCellLoadedTypeInfo.clone(),
            stateInitialized = stateInitialized,
            globalStructuralConstraintsHolder = globalStructuralConstraintsHolder,
            allowFailures = allowFailures,
            contractStack = contractStack,
            currentContract = currentContract,
            addressToHash = addressToHash,
            fetchedValues = fetchedValues,
            additionalFlags = additionalFlags,
            cellDataFieldManager = cellDataFieldManager.clone(),
            messageQueue = messageQueue,
            lastMsgBody = lastMsgBody,
            intercontractPath = intercontractPath,
            phase = phase,
            analysisOfGetMethod = analysisOfGetMethod,
        ).also { newState ->
            newState.dataCellInfoStorage = dataCellInfoStorage.clone()
            newState.contractIdToInitialData = contractIdToInitialData
            newState.contractIdToFirstElementOfC7 = contractIdToFirstElementOfC7
            newState.registersOfCurrentContract = registersOfCurrentContract.clone()
            newState.contractIdToC4Register = contractIdToC4Register
            newState.stack = stack.clone()
            newState.input = input
        }
    }

    override fun toString(): String = buildString {
        appendLine("Instruction: $lastStmt")
        if (isExceptional) appendLine("Exception: $methodResult")
        appendLine(continuationStack)
    }

    fun generateSymbolicRef(referenceType: TvmRealReferenceType): UConcreteHeapRef =
        memory.allocStatic(referenceType).also { symbolicRefs = symbolicRefs.add(it.address) }

    fun ensureSymbolicRefInitialized(
        ref: UHeapRef,
        referenceType: TvmRealReferenceType,
        initializer: TvmState.(UConcreteHeapRef) -> Unit = {}
    ) {
        if (!isStaticHeapRef(ref)) return

        val refs = symbolicRefs.add(ref.address)
        if (refs === symbolicRefs) return

        symbolicRefs = refs
        memory.types.allocate(ref.address, referenceType)
        initializer(ref)
    }
}

enum class TmvPhase {
    COMPUTE_PHASE,
    ACTION_PHASE,
    EXIT_PHASE,
    TERMINATED,
}

data class TvmCommitedState(
    val c4: C4Register,
    val c5: C5Register,
)

data class TvmContractPosition(
    val contractId: ContractId,
    val inst: TvmInst,
    val executionMemory: TvmContractExecutionMemory,
    val stackEntriesToTake: Int, // number of entries to fetch from the upper contract (from the point of [TvmState.contractStack]) when it exited
)

data class TvmContractExecutionMemory(
    val stack: TvmStack,
    val registers: TvmRegisters,
)