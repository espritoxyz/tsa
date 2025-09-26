package org.usvm.machine.types.memory.stack

import io.ksmt.sort.KBvSort
import kotlinx.collections.immutable.PersistentList
import org.ton.TlbStructure
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmStructuralError
import org.usvm.machine.types.TvmCellDataTypeRead
import org.usvm.machine.types.TvmDataCellLoadedTypeInfo

fun buildFrameForStructure(
    ctx: TvmContext,
    struct: TlbStructure,
    path: PersistentList<Int>,
    leftTlbDepth: Int,
): TlbStackFrame? {
    val tlbLevel = path.size
    return when (struct) {
        is TlbStructure.Unknown -> {
            check(tlbLevel == 0) {
                "`Unknown` is possible only on zero tlb level, but got tlb level $tlbLevel"
            }
            StackFrameOfUnknown
        }

        is TlbStructure.Empty -> {
            null
        }

        is TlbStructure.LoadRef -> {
            buildFrameForStructure(
                ctx,
                struct.rest,
                path,
                leftTlbDepth,
            )
        }

        is TlbStructure.KnownTypePrefix -> {
            KnownTypeTlbStackFrame(struct, path, leftTlbDepth)
        }

        is TlbStructure.SwitchPrefix -> {
            if (struct.variants.size > 1) {
                SwitchTlbStackFrame(struct, path, leftTlbDepth)
            } else {
                val variant = struct.variants.single()
                ConstTlbStackFrame(variant.key, variant.struct, ctx.zeroSizeExpr, path, leftTlbDepth)
            }
        }
    }
}

sealed interface TlbStackFrame {
    val path: List<Int>
    val leftTlbDepth: Int

    fun <ReadResult> step(
        state: TvmState,
        loadData: LimitedLoadData<ReadResult>,
    ): List<GuardedResult<ReadResult>>

    fun expandNewStackFrame(ctx: TvmContext): TlbStackFrame?

    val isSkippable: Boolean

    fun skipLabel(ctx: TvmContext): TlbStackFrame?

    fun readInModel(read: TlbStack.ConcreteReadInfo): Triple<String, TlbStack.ConcreteReadInfo, List<TlbStackFrame>>

    fun compareWithOtherFrame(
        scope: TvmStepScopeManager,
        cellRef: UConcreteHeapRef,
        otherFrame: TlbStackFrame,
        otherCellRef: UConcreteHeapRef,
    ): Pair<UBoolExpr?, Unit?>

    data class GuardedResult<ReadResult>(
        val guard: UBoolExpr,
        val result: StackFrameStepResult<ReadResult>,
        val value: ReadResult?,
    )
}

sealed interface StackFrameStepResult<out ReadResult>

data class StepError(
    val error: TvmStructuralError?,
) : StackFrameStepResult<Nothing>

/**
 * Represents that the top frame must be replaced by [frame] parameter.
 * @param concreteLoaded stores a bitvector that was stored after passing the stack frame
 */
data class NextFrame(
    val frame: TlbStackFrame,
    val concreteLoaded: UExpr<KBvSort>? = null,
) : StackFrameStepResult<Nothing>

data object EndOfStackFrame : StackFrameStepResult<Nothing>

/**
 * @param loadData the action that must be applied to the stack that was created after partially the partial loading
 * that spans across multiple Tlb frames.
 */
data class ContinueLoadOnNextFrame<ReadResult>(
    val loadData: LimitedLoadData<ReadResult>,
    val concreteLoaded: UExpr<KBvSort>? = null,
) : StackFrameStepResult<ReadResult>

data class LimitedLoadData<ReadResult>(
    val cellAddress: UConcreteHeapRef,
    val type: TvmCellDataTypeRead<ReadResult>,
) {
    companion object {
        fun <ReadResult> fromLoadData(loadData: TvmDataCellLoadedTypeInfo.LoadData<ReadResult>) =
            LimitedLoadData(
                type = loadData.type,
                cellAddress = loadData.cellAddress,
            )
    }
}
