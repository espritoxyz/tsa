package org.usvm.machine.types.memory.stack

import io.ksmt.expr.KBitVecValue
import org.ton.TlbStructure
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.state.TvmState
import org.usvm.machine.types.TvmCellDataTypeReadValue
import org.usvm.machine.types.memory.UnknownBlockField
import org.usvm.machine.types.memory.stack.TlbStackFrame.GuardedResult

data object StackFrameOfUnknown : TlbStackFrame {
    // [Unknown] can be used only on zero TL-B level
    override val path = emptyList<Int>()
    override val leftTlbDepth: Int = 0

    override fun <ReadResult : TvmCellDataTypeReadValue> step(
        state: TvmState,
        loadData: LimitedLoadData<ReadResult>
    ): List<GuardedResult<ReadResult>> =
        listOf(GuardedResult(state.ctx.trueExpr, NextFrame(this), value = null))

    override fun expandNewStackFrame(ctx: TvmContext): TlbStackFrame? = null

    override val isSkippable: Boolean = false

    override fun skipLabel(ctx: TvmContext): TlbStackFrame? = null

    override fun readInModel(
        read: TlbStack.ConcreteReadInfo
    ): Triple<String, TlbStack.ConcreteReadInfo, List<TlbStackFrame>> = with(read.resolver.state.ctx) {
        val field = UnknownBlockField(TlbStructure.Unknown.id, path)
        val dataSymbolic = read.resolver.state.memory.readField(read.address, field, field.getSort(this))
        val data = (read.resolver.model.eval(dataSymbolic) as KBitVecValue<*>).stringValue

        val newReadInfo = TlbStack.ConcreteReadInfo(read.address, read.resolver, leftBits = 0)

        Triple(data.take(read.leftBits), newReadInfo, emptyList())
    }
}