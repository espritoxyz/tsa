package org.usvm.machine.types.memory.stack

import io.ksmt.expr.KExpr
import io.ksmt.expr.KInterpretedValue
import kotlinx.collections.immutable.PersistentList
import org.ton.TlbStructure
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.intValue
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmStructuralError
import org.usvm.machine.state.calcOnStateCtx
import org.usvm.machine.types.SizedCellDataTypeRead
import org.usvm.machine.types.TvmCellDataBitArrayRead
import org.usvm.machine.types.TvmCellDataCoinsRead
import org.usvm.machine.types.TvmCellDataMsgAddrRead
import org.usvm.machine.types.TvmCellDataTypeRead
import org.usvm.machine.types.TvmReadingOutOfSwitchBounds
import org.usvm.machine.types.TvmReadingSwitchWithUnexpectedType
import org.usvm.machine.types.memory.readConcreteBv
import org.usvm.machine.types.memory.stack.TlbStackFrame.GuardedResult
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
    override fun <ReadResult> step(
        scope: TvmStepScopeManager,
        loadData: LimitedLoadData<ReadResult>,
        badCellSizeIsExceptional: Boolean,
        onBadCellSize: (TvmState, BadSizeContext) -> Unit,
    ): List<GuardedResult<ReadResult>> =
        scope.calcOnStateCtx {
            val concreteOffset = if (offset is KInterpretedValue) offset.intValue() else null
            val leftBits = mkSizeSubExpr(mkSizeExpr(data.length), offset)

            val type = loadData.type
            val readSize =
                extractReadSizeFromType(type, concreteOffset) ?: return@calcOnStateCtx listOf(
                    GuardedResult(
                        trueExpr,
                        StepError(
                            TvmStructuralError(
                                TvmReadingSwitchWithUnexpectedType(type),
                                phase,
                            ),
                        ),
                        value = null,
                    ),
                )

            val concreteBvRead = readConcreteBv(ctx, concreteOffset, data, readSize)
            // full read of constant
            val stepResult =
                buildFrameForStructure(
                    ctx,
                    nextStruct,
                    path,
                    leftTlbDepth,
                )?.let {
                    NextFrame(it, concreteBvRead)
                } ?: EndOfStackFrame

            val value = type.readFromConstant(this, offset, data)

            val result =
                mutableListOf(
                    GuardedResult(
                        mkSizeLtExpr(readSize, leftBits),
                        NextFrame(
                            ConstTlbStackFrame(data, nextStruct, mkSizeAddExpr(offset, readSize), path, leftTlbDepth),
                            concreteBvRead,
                        ),
                        value,
                    ),
                    GuardedResult(
                        readSize eq leftBits,
                        stepResult,
                        value,
                    ),
                )

            if (type is TvmCellDataBitArrayRead) {
                val curGuard = loadData.guard and mkSizeGtExpr(readSize, leftBits)
                result.add(
                    GuardedResult(
                        mkSizeGtExpr(readSize, leftBits),
                        ContinueLoadOnNextFrame(
                            LimitedLoadData(
                                loadData.cellRef,
                                curGuard,
                                type.createLeftBitsDataLoad(mkSizeSubExpr(readSize, leftBits)),
                            ),
                            concreteBvRead,
                        ),
                        value = value,
                    ),
                )
            } else {
                result.add(
                    GuardedResult(
                        mkSizeGtExpr(readSize, leftBits),
                        StepError(
                            TvmStructuralError(
                                TvmReadingOutOfSwitchBounds(type),
                                phase,
                            ),
                        ),
                        value = null,
                    ),
                )
            }

            result
        }

    /**
     * @param concreteOffset if `null` if the offset is not concrete
     */
    private fun <ReadResult> TvmContext.extractReadSizeFromType(
        type: TvmCellDataTypeRead<ReadResult>,
        concreteOffset: Int?,
    ): KExpr<TvmSizeSort>? =
        if (type is SizedCellDataTypeRead) {
            type.sizeBits
        } else if (type is TvmCellDataCoinsRead && concreteOffset != null) {
            val fourDataBits =
                data.substring(concreteOffset, min(concreteOffset + 4, data.length))
            if (fourDataBits == "0000") {
                // special case when reading const with coin read is possible
                fourSizeExpr
            } else {
                null
            }
        } else if (type is TvmCellDataMsgAddrRead && concreteOffset != null) {
            val tlbPrefix =
                data.substring(concreteOffset, min(concreteOffset + 2, data.length))
            when (tlbPrefix) {
                "00" -> twoSizeExpr
                "10" -> {
                    val noAnycast = data.getOrNull(concreteOffset + 2) == '0'
                    if (noAnycast) {
                        val tlbPrefixSize = 2
                        val nothingCtorSize = 1
                        val workchainIdSize = 8
                        val addressBitsSize = 256
                        (tlbPrefixSize + nothingCtorSize + workchainIdSize + addressBitsSize).toSizeSort()
                    } else {
                        null // unsupported
                    }
                }

                else -> null
            }
        } else {
            null
        }

    override fun expandNewStackFrame(ctx: TvmContext): TlbStackFrame? = null

    override fun skipLabel(
        state: TvmState,
        ref: UConcreteHeapRef,
    ): TlbStackFrame.SkipResult =
        buildFrameForStructure(state.ctx, nextStruct, path, leftTlbDepth)?.let {
            TlbStackFrame.NextFrame(it)
        } ?: TlbStackFrame.EndOfFrame

    override fun readInModel(
        read: TlbStack.ConcreteReadInfo,
    ): Triple<String, TlbStack.ConcreteReadInfo, List<TlbStackFrame>> {
        check(read.leftBits >= data.length)
        val newReadInfo =
            TlbStack.ConcreteReadInfo(
                read.ref,
                read.resolver,
                read.leftBits - data.length,
            )
        val further = buildFrameForStructure(read.resolver.state.ctx, nextStruct, path, leftTlbDepth)
        val newFrames = further?.let { listOf(it) } ?: emptyList()
        return Triple(data, newReadInfo, newFrames)
    }

    override fun compareWithOtherFrame(
        scope: TvmStepScopeManager,
        cellRef: UConcreteHeapRef,
        otherFrame: TlbStackFrame,
        otherCellRef: UConcreteHeapRef,
    ): Pair<UBoolExpr?, Unit?> =
        with(scope.ctx) {
            if (otherFrame !is ConstTlbStackFrame) {
                return null to Unit
            }

            if (offset !is KInterpretedValue<*> || otherFrame.offset !is KInterpretedValue<*>) {
                return null to Unit
            }

            val offset1 = offset.intValue()
            val offset2 = otherFrame.offset.intValue()

            val minSize = min(data.length - offset1, otherFrame.data.length - offset2)
            val data1 = data.substring(startIndex = offset1, endIndex = minSize + offset1)
            val data2 = otherFrame.data.substring(startIndex = offset2, endIndex = minSize + offset2)
            if (data1 != data2) {
                return falseExpr to Unit
            }

            if (data.length - offset1 != otherFrame.data.length - offset2) {
                return if (nextStruct is TlbStructure.Empty && otherFrame.nextStruct is TlbStructure.Empty) {
                    falseExpr to Unit
                } else {
                    null to Unit
                }
            }

            val nextFrame1 = buildFrameForStructure(this, nextStruct, path, leftTlbDepth)
            val nextFrame2 =
                buildFrameForStructure(this, otherFrame.nextStruct, otherFrame.path, otherFrame.leftTlbDepth)

            if (nextFrame1 == null && nextFrame2 == null) {
                return trueExpr to Unit
            }

            nextFrame1 ?: return null to Unit
            nextFrame2 ?: return null to Unit

            return nextFrame1.compareWithOtherFrame(scope, cellRef, nextFrame2, otherCellRef)
        }
}
