package org.usvm.machine.types.memory.stack

import kotlinx.collections.immutable.PersistentList
import org.ton.TlbStructure
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.machine.TvmContext
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmStructuralError
import org.usvm.machine.types.TvmCellDataTypeRead
import org.usvm.machine.types.TvmCellDataTypeReadValue
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

    fun <ReadResult : TvmCellDataTypeReadValue> step(
        state: TvmState,
        loadData: LimitedLoadData<ReadResult>,
    ): List<GuardedResult<ReadResult>>

    fun expandNewStackFrame(ctx: TvmContext): TlbStackFrame?

    val isSkippable: Boolean

    fun skipLabel(ctx: TvmContext): TlbStackFrame?

    fun readInModel(read: TlbStack.ConcreteReadInfo): Triple<String, TlbStack.ConcreteReadInfo, List<TlbStackFrame>>

    data class GuardedResult<ReadResult : TvmCellDataTypeReadValue>(
        val guard: UBoolExpr,
        val result: StackFrameStepResult<ReadResult>,
        val value: ReadResult?,
    )
}

sealed interface StackFrameStepResult<out ReadResult>

data class StepError(
    val error: TvmStructuralError?,
) : StackFrameStepResult<Nothing>

data class NextFrame(
    val frame: TlbStackFrame,
) : StackFrameStepResult<Nothing>

data object EndOfStackFrame : StackFrameStepResult<Nothing>

data class PassLoadToNextFrame<ReadResult : TvmCellDataTypeReadValue>(
    val loadData: LimitedLoadData<ReadResult>,
) : StackFrameStepResult<ReadResult>

data class LimitedLoadData<ReadResult : TvmCellDataTypeReadValue>(
    val cellAddress: UConcreteHeapRef,
    val type: TvmCellDataTypeRead<ReadResult>,
) {
    companion object {
        fun <ReadResult : TvmCellDataTypeReadValue> fromLoadData(
            loadData: TvmDataCellLoadedTypeInfo.LoadData<ReadResult>,
        ) = LimitedLoadData(
            type = loadData.type,
            cellAddress = loadData.cellAddress,
        )
    }
}
