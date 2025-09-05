package org.usvm.machine.types.memory.stack

import io.ksmt.expr.KInterpretedValue
import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.PersistentList
import org.ton.TlbStructure
import org.usvm.UExpr
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.intValue
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmStructuralError
import org.usvm.machine.types.SizedCellDataTypeRead
import org.usvm.machine.types.TvmCellDataBitArrayRead
import org.usvm.machine.types.TvmCellDataCoinsRead
import org.usvm.machine.types.TvmCellDataMsgAddrRead
import org.usvm.machine.types.TvmCellDataTypeReadValue
import org.usvm.machine.types.TvmReadingOutOfSwitchBounds
import org.usvm.machine.types.TvmReadingSwitchWithUnexpectedType
import org.usvm.machine.types.memory.stack.TlbStackFrame.GuardedResult
import org.usvm.machine.types.memory.readFromConstant
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGtExpr
import org.usvm.mkSizeLtExpr
import org.usvm.mkSizeSubExpr
import kotlin.math.min

data class ConstTlbStackFrame(
    private val data: String,
    private val nextStruct: TlbStructure,
    private val offset: UExpr<TvmSizeSort>,
    override val path: PersistentList<Int>,
    override val leftTlbDepth: Int,
) : TlbStackFrame {

    override fun <ReadResult : TvmCellDataTypeReadValue> step(
        state: TvmState,
        loadData: LimitedLoadData<ReadResult>,
    ): List<GuardedResult<ReadResult>> = with(state.ctx) {

        val concreteOffset = if (offset is KInterpretedValue) offset.intValue() else null
        val fourDataBits = if (concreteOffset != null) {
            data.substring(concreteOffset, min(concreteOffset + 4, data.length))
        } else {
            null
        }

        val leftBits = mkSizeSubExpr(mkSizeExpr(data.length), offset)

        val readSize = if (loadData.type is SizedCellDataTypeRead) {
            loadData.type.sizeBits

        } else if (loadData.type is TvmCellDataCoinsRead && concreteOffset != null && fourDataBits == "0000") {
            // special case when reading const with coin read is possible
            fourSizeExpr

        } else if (loadData.type is TvmCellDataMsgAddrRead && concreteOffset != null && fourDataBits?.startsWith("00") == true) {
            // special case when reading const with address read is possible (result is addr_none)
            twoSizeExpr

        } else {
            return@with listOf(
                GuardedResult(
                    trueExpr,
                    StepError(
                        TvmStructuralError(TvmReadingSwitchWithUnexpectedType(loadData.type), state.phase)
                    ),
                    value = null,
                )
            )
        }

        // full read of constant
        val stepResult = buildFrameForStructure(
            state.ctx,
            nextStruct,
            path,
            leftTlbDepth,
        )?.let {
            NextFrame(it)
        } ?: EndOfStackFrame

        val value = loadData.type.readFromConstant(state, offset, data)

        val result = mutableListOf(
            GuardedResult(
                mkSizeLtExpr(readSize, leftBits),
                NextFrame(ConstTlbStackFrame(data, nextStruct, mkSizeAddExpr(offset, readSize), path, leftTlbDepth)),
                value,
            ),
            GuardedResult(
                readSize eq leftBits,
                stepResult,
                value,
            )
        )

        if (loadData.type is TvmCellDataBitArrayRead) {
            result.add(
                GuardedResult(
                    mkSizeGtExpr(readSize, leftBits),
                    PassLoadToNextFrame(
                        LimitedLoadData(
                            loadData.cellAddress,
                            TvmCellDataBitArrayRead(mkSizeSubExpr(readSize, leftBits)).uncheckedCast(),
                        )
                    ),
                    value = null,
                )
            )
        } else {
            result.add(
                GuardedResult(
                    mkSizeGtExpr(readSize, leftBits),
                    StepError(TvmStructuralError(TvmReadingOutOfSwitchBounds(loadData.type), state.phase)),
                    value = null,
                )
            )
        }

        return result
    }

    override fun expandNewStackFrame(ctx: TvmContext): TlbStackFrame? {
        return null
    }

    override val isSkippable: Boolean = true

    override fun skipLabel(ctx: TvmContext): TlbStackFrame? {
        return buildFrameForStructure(ctx, nextStruct, path, leftTlbDepth)
    }

    override fun readInModel(read: TlbStack.ConcreteReadInfo): Triple<String, TlbStack.ConcreteReadInfo, List<TlbStackFrame>> {
        check(read.leftBits >= data.length) {
            "Unexpected read. Reading ${read.leftBits}, but have only ${data.length} bits left."
        }
        val newReadInfo = TlbStack.ConcreteReadInfo(
            read.address,
            read.resolver,
            read.leftBits - data.length,
        )
        val further = buildFrameForStructure(read.resolver.state.ctx, nextStruct, path, leftTlbDepth)
        val newFrames = further?.let { listOf(it) } ?: emptyList()
        return Triple(data, newReadInfo, newFrames)
    }
}
