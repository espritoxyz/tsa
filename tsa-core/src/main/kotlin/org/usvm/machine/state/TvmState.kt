package org.usvm.machine.state

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentListOf
import org.ton.bytecode.TvmCodeBlock
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
import org.usvm.constraints.UPathConstraints
import org.usvm.isStaticHeapRef
import org.usvm.machine.TvmContext
import org.usvm.machine.types.GlobalStructuralConstraintsHolder
import org.usvm.machine.types.TvmDataCellInfoStorage
import org.usvm.machine.types.TvmDataCellLoadedTypeInfo
import org.usvm.machine.types.TvmRealReferenceType
import org.usvm.machine.types.TvmType
import org.usvm.machine.types.TvmTypeSystem
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet

class TvmState(
    ctx: TvmContext,
    override val entrypoint: TvmCodeBlock,
//    val registers: TvmRegisters, // TODO do we really need keep the registers this way?
    var stack: TvmStack = TvmStack(ctx, persistentListOf()),
    var registers: TvmRegisters,
    val emptyRefValue: TvmRefEmptyValue,
    private var symbolicRefs: PersistentSet<UConcreteHeapAddress> = persistentHashSetOf(),
    var gasUsage: PersistentList<UExpr<UBv32Sort>>,
    // TODO codepage
    callStack: UCallStack<TvmCodeBlock, TvmInst> = UCallStack(),
    pathConstraints: UPathConstraints<TvmType>,
    memory: UMemory<TvmType, TvmCodeBlock>,
    models: List<UModelBase<TvmType>> = listOf(),
    pathNode: PathNode<TvmInst> = PathNode.root(),
    forkPoints: PathNode<PathNode<TvmInst>> = PathNode.root(),
    var methodResult: TvmMethodResult = TvmMethodResult.NoCall,
    targets: UTargetsSet<TvmTarget, TvmInst> = UTargetsSet.empty(),
    val typeSystem: TvmTypeSystem,
    var commitedState: TvmCommitedState? = null,
    val dataCellLoadedTypeInfo: TvmDataCellLoadedTypeInfo = TvmDataCellLoadedTypeInfo.empty(),
    var stateInitialized: Boolean = false,
    val globalStructuralConstraintsHolder: GlobalStructuralConstraintsHolder = GlobalStructuralConstraintsHolder(),
) : UState<TvmType, TvmCodeBlock, TvmInst, TvmContext, TvmTarget, TvmState>(
    ctx,
    callStack,
    pathConstraints,
    memory,
    models,
    pathNode,
    forkPoints,
    targets,
) {
    override val isExceptional: Boolean
        get() = methodResult is TvmMethodResult.TvmFailure || methodResult is TvmMethodResult.TvmStructuralError

    lateinit var initialData: TvmInitialStateData
    lateinit var dataCellInfoStorage: TvmDataCellInfoStorage

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
        val clonedConstraints = newConstraints ?: pathConstraints.clone()

        return TvmState(
            ctx = ctx,
            entrypoint = entrypoint,
            stack = stack.clone(),
            registers = registers.clone(),
            emptyRefValue = emptyRefValue,
            symbolicRefs = symbolicRefs,
            gasUsage = gasUsage,
            callStack = callStack.clone(),
            pathConstraints = clonedConstraints,
            memory = memory.clone(clonedConstraints.typeConstraints),
            models = models,
            pathNode = pathNode,
            forkPoints = forkPoints,
            methodResult = methodResult,
            targets = targets.clone(),
            typeSystem = typeSystem,
            commitedState = commitedState,
            dataCellLoadedTypeInfo = dataCellLoadedTypeInfo.clone(),
            stateInitialized = stateInitialized,
            globalStructuralConstraintsHolder = globalStructuralConstraintsHolder,
        ).also { newState ->
            newState.dataCellInfoStorage = dataCellInfoStorage.clone()
            newState.initialData = initialData
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

        memory.types.allocate(ref.address, referenceType)
        initializer(ref)
    }
}

data class TvmCommitedState(
    val c4: C4Register,
    val c5: C5Register,
)
