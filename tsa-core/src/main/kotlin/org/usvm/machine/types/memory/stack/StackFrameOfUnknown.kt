package org.usvm.machine.types.memory.stack

import io.ksmt.expr.KBitVecValue
import kotlinx.collections.immutable.PersistentList
import org.ton.TlbStructure
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmState
import org.usvm.machine.types.memory.UnknownBlockField
import org.usvm.machine.types.memory.stack.TlbStackFrame.GuardedResult

data class StackFrameOfUnknown(
    override val path: PersistentList<Int>,
    override val leftTlbDepth: Int,
) : TlbStackFrame {
    override fun <ReadResult> step(
        state: TvmState,
        loadData: LimitedLoadData<ReadResult>,
    ): List<GuardedResult<ReadResult>> = listOf(GuardedResult(state.ctx.trueExpr, NextFrame(this), value = null))

    override fun expandNewStackFrame(ctx: TvmContext): TlbStackFrame? = null

    override val isSkippable: Boolean = false

    override fun skipLabel(ctx: TvmContext): TlbStackFrame? = null

    override fun readInModel(
        read: TlbStack.ConcreteReadInfo,
    ): Triple<String, TlbStack.ConcreteReadInfo, List<TlbStackFrame>> =
        with(read.resolver.state.ctx) {
            val inferredStruct =
                read.resolver.state.fieldManagers.cellDataFieldManager.inferenceManager.getInferredStruct(
                    read.address,
                    path,
                )

            if (inferredStruct == null) {
                val field = UnknownBlockField(TlbStructure.Unknown.id, path)
                val dataSymbolic =
                    read.resolver.state.memory
                        .readField(read.address, field, field.getSort(this))
                val data = (read.resolver.model.eval(dataSymbolic) as KBitVecValue<*>).stringValue

                val newReadInfo = TlbStack.ConcreteReadInfo(read.address, read.resolver, leftBits = 0)

                Triple(data.take(read.leftBits), newReadInfo, emptyList())
            } else {
                val nextFrame =
                    buildFrameForStructure(this, inferredStruct, path.add(TlbStructure.Unknown.id), leftTlbDepth)
                        ?: error("Unexpected null frame")

                nextFrame.readInModel(read)
            }
        }

    override fun compareWithOtherFrame(
        scope: TvmStepScopeManager,
        cellRef: UConcreteHeapRef,
        otherFrame: TlbStackFrame,
        otherCellRef: UConcreteHeapRef,
    ): Pair<UBoolExpr?, Unit?> = null to Unit
}
