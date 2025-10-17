package org.usvm.machine.types.memory.stack

import io.ksmt.expr.KBitVecValue
import io.ksmt.sort.KBvSort
import kotlinx.collections.immutable.PersistentList
import org.ton.FixedSizeDataLabel
import org.ton.TlbAddressByRef
import org.ton.TlbAtomicLabel
import org.ton.TlbBitArrayByRef
import org.ton.TlbBuiltinLabel
import org.ton.TlbCompositeLabel
import org.ton.TlbIntegerLabelOfSymbolicSize
import org.ton.TlbSliceByRefInBuilder
import org.ton.TlbStructure
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.intValue
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmStructuralError
import org.usvm.machine.state.calcOnStateCtx
import org.usvm.machine.state.slicesAreEqual
import org.usvm.machine.types.ContinueLoadOnNextFrameData
import org.usvm.machine.types.TvmCellDataBitArrayRead
import org.usvm.machine.types.TvmReadingOfUnexpectedType
import org.usvm.machine.types.accepts
import org.usvm.machine.types.isEmptyLabel
import org.usvm.machine.types.memory.ConcreteSizeBlockField
import org.usvm.machine.types.memory.SliceRefField
import org.usvm.machine.types.memory.SymbolicSizeBlockField
import org.usvm.machine.types.memory.extractKBvOfConcreteSizeFromTlbIfPossible
import org.usvm.machine.types.memory.extractTlbValueIfPossible
import org.usvm.machine.types.memory.stack.TlbStackFrame.GuardedResult
import org.usvm.machine.types.memory.typeArgs
import org.usvm.machine.types.passBitArrayRead
import org.usvm.test.resolver.TvmTestSliceValue

data class KnownTypeTlbStackFrame(
    val struct: TlbStructure.KnownTypePrefix,
    override val path: PersistentList<Int>,
    override val leftTlbDepth: Int,
) : TlbStackFrame {
    override fun <ReadResult> step(
        scope: TvmStepScopeManager,
        loadData: LimitedLoadData<ReadResult>,
    ): List<GuardedResult<ReadResult>> =
        scope.calcOnStateCtx {
            if (struct.typeLabel !is TlbBuiltinLabel) {
                return@calcOnStateCtx listOf(GuardedResult(trueExpr, StepError(error = null), value = null))
            }

            val args = struct.typeArgs(this, loadData.cellRef, path)

            val frameIsEmpty = struct.typeLabel.isEmptyLabel(ctx, args)

            val continueLoadingOnNextFrameData = createContinueLoadingOnNextFrame(loadData, struct.typeLabel, args)

            val continueReadOnNextFrameCondition =
                continueLoadingOnNextFrameData?.let {
                    continueLoadingOnNextFrameData.guard or frameIsEmpty
                } ?: frameIsEmpty

            val accept = struct.typeLabel.accepts(ctx, args, loadData.type)
            val readBvValue =
                if (loadData.type is TvmCellDataBitArrayRead) {
                    extractKBvOfConcreteSizeFromTlbIfPossible(
                        struct,
                        loadData.cellRef,
                        path,
                        this,
                    )
                } else {
                    null
                }
            val nextFrame =
                buildFrameForStructure(
                    ctx,
                    struct.rest,
                    path,
                    leftTlbDepth,
                )?.let {
                    NextFrame(it, readBvValue)
                } ?: EndOfStackFrame

            val error = createStepError(struct.typeLabel, args, loadData, this)
            val value =
                struct.typeLabel.extractTlbValueIfPossible(
                    struct,
                    loadData.type,
                    loadData.cellRef,
                    path,
                    this,
                    leftTlbDepth,
                )

            val result: MutableList<GuardedResult<ReadResult>> =
                mutableListOf(
                    GuardedResult(frameIsEmpty, ContinueLoadOnNextFrame(loadData), null),
                    GuardedResult(continueReadOnNextFrameCondition.not() and accept, nextFrame, value),
                    GuardedResult(continueReadOnNextFrameCondition.not() and accept.not(), StepError(error), null),
                )

            if (continueLoadingOnNextFrameData != null) {
                require(loadData.type is TvmCellDataBitArrayRead) {
                    "loading across different frames is not supported for non-bit-array reads"
                }

                val continueLoadOnNextFrameAction =
                    createLoadAcrossFramesAction(
                        loadData,
                        continueLoadingOnNextFrameData.leftBits,
                        readBvValue,
                    )
                result.add(
                    GuardedResult(
                        continueLoadingOnNextFrameData.guard,
                        continueLoadOnNextFrameAction,
                        value = value,
                    ),
                )
            }

            result
        }

    private fun <ReadResult> createLoadAcrossFramesAction(
        loadData: LimitedLoadData<ReadResult>,
        leftBits: UExpr<TvmSizeSort>,
        bvValue: UExpr<KBvSort>?,
    ): ContinueLoadOnNextFrame<ReadResult> {
        val type = loadData.type
        check(type is TvmCellDataBitArrayRead)
        return ContinueLoadOnNextFrame(
            LimitedLoadData(
                loadData.cellRef,
                loadData.type.createLeftBitsDataLoad(leftBits),
            ),
            bvValue,
        )
    }

    private fun <ReadResult> createStepError(
        expectedLabel: TlbBuiltinLabel,
        args: List<UExpr<TvmSizeSort>>,
        loadData: LimitedLoadData<ReadResult>,
        state: TvmState,
    ): TvmStructuralError =
        TvmStructuralError(
            TvmReadingOfUnexpectedType(
                expectedLabel,
                args,
                loadData.type,
            ),
            state.phase,
            state.stack,
        )

    private fun <ReadResult> TvmContext.createContinueLoadingOnNextFrame(
        loadData: LimitedLoadData<ReadResult>,
        typeLabel: TlbBuiltinLabel,
        args: List<UExpr<TvmSizeSort>>,
    ): ContinueLoadOnNextFrameData? =
        when (loadData.type) {
            is TvmCellDataBitArrayRead -> {
                typeLabel.passBitArrayRead(this, args, loadData.type.sizeBits)
            }

            else -> {
                null
            }
        }

    override fun expandNewStackFrame(ctx: TvmContext): TlbStackFrame? {
        require(leftTlbDepth > 0)
        return when (struct.typeLabel) {
            is TlbAtomicLabel -> {
                null
            }

            is TlbCompositeLabel -> {
                buildFrameForStructure(
                    ctx,
                    struct.typeLabel.internalStructure,
                    path.add(struct.id),
                    leftTlbDepth - 1,
                )
            }
        }
    }

    override val isSkippable: Boolean = true

    override fun skipLabel(ctx: TvmContext) = buildFrameForStructure(ctx, struct.rest, path, leftTlbDepth)

    override fun readInModel(
        read: TlbStack.ConcreteReadInfo,
    ): Triple<String, TlbStack.ConcreteReadInfo, List<TlbStackFrame>> =
        with(read.resolver.state.ctx) {
            val state = read.resolver.state
            val model = read.resolver.model
            when (struct.typeLabel) {
                is TlbCompositeLabel -> {
                    val newFrame =
                        expandNewStackFrame(state.ctx)
                            ?: error("Could not expand new frame for struct $struct")
                    Triple("", read, listOf(this@KnownTypeTlbStackFrame, newFrame))
                }

                is FixedSizeDataLabel -> {
                    check(read.leftBits >= struct.typeLabel.concreteSize) {
                        "Unexpected left bits value: ${read.leftBits} while reading ${struct.typeLabel}"
                    }

                    val field = ConcreteSizeBlockField(struct.typeLabel.concreteSize, struct.id, path)
                    val contentSymbolic = state.memory.readField(read.address, field, field.getSort(this))
                    val content = model.eval(contentSymbolic)
                    val bits = (content as? KBitVecValue)?.stringValue ?: error("Unexpected expr $content")

                    val newRead =
                        TlbStack.ConcreteReadInfo(
                            read.address,
                            read.resolver,
                            read.leftBits - struct.typeLabel.concreteSize,
                        )

                    val newFrame = skipLabel(this)

                    Triple(bits, newRead, newFrame?.let { listOf(it) } ?: emptyList())
                }

                is TlbIntegerLabelOfSymbolicSize -> {
                    val typeArgs = struct.typeArgs(state, read.address, path)
                    val intSizeSymbolic = struct.typeLabel.bitSize(state.ctx, typeArgs)
                    val intSize = model.eval(intSizeSymbolic).intValue()
                    check(read.leftBits >= intSize)

                    val field = SymbolicSizeBlockField(struct.typeLabel.lengthUpperBound, struct.id, path)
                    val intValueSymbolic = state.memory.readField(read.address, field, field.getSort(this))
                    val intValue = (model.eval(intValueSymbolic) as KBitVecValue<*>).stringValue

                    val intValueBinaryTrimmed = intValue.takeLast(intSize)

                    val newRead =
                        TlbStack.ConcreteReadInfo(
                            read.address,
                            read.resolver,
                            read.leftBits - intSize,
                        )

                    val newFrame = skipLabel(this)

                    Triple(intValueBinaryTrimmed, newRead, newFrame?.let { listOf(it) } ?: emptyList())
                }

                is TlbBitArrayByRef, is TlbAddressByRef -> {
                    val field = SliceRefField(struct.id, path)
                    val slice = state.memory.readField(read.address, field, field.getSort(this))

                    val content =
                        read.resolver.resolveRef(slice) as? TvmTestSliceValue
                            ?: error("$slice must evaluate to slice")

                    val curData = content.cell.data.drop(content.dataPos)

                    val newRead =
                        TlbStack.ConcreteReadInfo(
                            read.address,
                            read.resolver,
                            read.leftBits - curData.length,
                        )

                    val newFrame = skipLabel(this)

                    Triple(curData, newRead, newFrame?.let { listOf(it) } ?: emptyList())
                }
            }
        }

    override fun compareWithOtherFrame(
        scope: TvmStepScopeManager,
        cellRef: UConcreteHeapRef,
        otherFrame: TlbStackFrame,
        otherCellRef: UConcreteHeapRef,
    ): Pair<UBoolExpr?, Unit?> =
        with(scope.ctx) {
            if (otherFrame !is KnownTypeTlbStackFrame) {
                return null to Unit
            }

            if (struct.typeLabel.arity != 0) {
                return null to Unit
            }

            val curGuard =
                when (struct.typeLabel) {
                    is FixedSizeDataLabel -> {
                        if (otherFrame.struct.typeLabel != struct.typeLabel) {
                            return null to Unit
                        }

                        val field = ConcreteSizeBlockField(struct.typeLabel.concreteSize, struct.id, path)
                        val content =
                            scope.calcOnState {
                                memory.readField(cellRef, field, field.getSort(ctx))
                            }

                        val fieldOther =
                            ConcreteSizeBlockField(struct.typeLabel.concreteSize, otherFrame.struct.id, otherFrame.path)
                        val contentOther =
                            scope.calcOnState {
                                memory.readField(otherCellRef, fieldOther, fieldOther.getSort(ctx))
                            }

                        content eq contentOther
                    }

                    is TlbSliceByRefInBuilder -> {
                        if (otherFrame.struct.typeLabel !is TlbSliceByRefInBuilder) {
                            return null to Unit
                        }

                        val canCompare =
                            struct.typeLabel.sizeBits == otherFrame.struct.typeLabel.sizeBits ||
                                struct.rest is TlbStructure.Empty &&
                                otherFrame.struct.rest is TlbStructure.Empty

                        if (!canCompare) {
                            return null to Unit
                        }

                        val field = SliceRefField(struct.id, path)
                        val slice =
                            scope.calcOnState {
                                memory.readField(cellRef, field, field.getSort(ctx))
                            }

                        val fieldOther = SliceRefField(otherFrame.struct.id, otherFrame.path)
                        val sliceOther =
                            scope.calcOnState {
                                memory.readField(otherCellRef, fieldOther, fieldOther.getSort(ctx))
                            }

                        scope.slicesAreEqual(slice, sliceOther)
                            ?: return null to null
                    }

                    is TlbIntegerLabelOfSymbolicSize, is TlbCompositeLabel -> {
                        return null to Unit
                    }
                }

            val nextFrame1 = buildFrameForStructure(this, struct.rest, path, leftTlbDepth)
            val nextFrame2 =
                buildFrameForStructure(this, otherFrame.struct.rest, otherFrame.path, otherFrame.leftTlbDepth)

            if (nextFrame1 == null && nextFrame2 == null) {
                return curGuard to Unit
            }

            nextFrame1 ?: return null to Unit
            nextFrame2 ?: return null to Unit

            val (further, status) = nextFrame1.compareWithOtherFrame(scope, cellRef, nextFrame2, otherCellRef)

            status ?: return null to null

            return further?.let { curGuard and it } to Unit
        }
}
