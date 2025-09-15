package org.usvm.machine.types.memory.stack

import io.ksmt.expr.KBitVecValue
import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.PersistentList
import org.ton.FixedSizeDataLabel
import org.ton.TlbAddressByRef
import org.ton.TlbAtomicLabel
import org.ton.TlbBitArrayByRef
import org.ton.TlbBuiltinLabel
import org.ton.TlbCompositeLabel
import org.ton.TlbIntegerLabelOfSymbolicSize
import org.ton.TlbStructure
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.intValue
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmStructuralError
import org.usvm.machine.types.TvmCellDataBitArrayRead
import org.usvm.machine.types.TvmCellDataTypeReadValue
import org.usvm.machine.types.TvmReadingOfUnexpectedType
import org.usvm.machine.types.accepts
import org.usvm.machine.types.isEmptyLabel
import org.usvm.machine.types.memory.ConcreteSizeBlockField
import org.usvm.machine.types.memory.SliceRefField
import org.usvm.machine.types.memory.SymbolicSizeBlockField
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
    override fun <ReadResult : TvmCellDataTypeReadValue> step(
        state: TvmState,
        loadData: LimitedLoadData<ReadResult>,
    ): List<GuardedResult<ReadResult>> =
        with(state.ctx) {
            if (struct.typeLabel !is TlbBuiltinLabel) {
                return listOf(GuardedResult(trueExpr, StepError(error = null), value = null))
            }

            val args = struct.typeArgs(state, loadData.cellAddress, path)

            val frameIsEmpty = struct.typeLabel.isEmptyLabel(this, args)

            val passPartOfLoadDataFurther =
                if (loadData.type is TvmCellDataBitArrayRead) {
                    struct.typeLabel.passBitArrayRead(this, args, loadData.type.sizeBits)
                } else {
                    null
                }

            val continueReadOnNextFrame =
                passPartOfLoadDataFurther?.let {
                    passPartOfLoadDataFurther.guard or frameIsEmpty
                } ?: frameIsEmpty

            val accept = struct.typeLabel.accepts(state.ctx, args, loadData.type)
            val nextFrame =
                buildFrameForStructure(
                    state.ctx,
                    struct.rest,
                    path,
                    leftTlbDepth,
                )?.let {
                    NextFrame(it)
                } ?: EndOfStackFrame

            val error =
                TvmStructuralError(
                    TvmReadingOfUnexpectedType(
                        struct.typeLabel,
                        args,
                        loadData.type,
                    ),
                    state.phase,
                    state.stack,
                )

            val value =
                struct.typeLabel.extractTlbValueIfPossible(
                    struct,
                    loadData.type,
                    loadData.cellAddress,
                    path,
                    state,
                    leftTlbDepth,
                )

            val result: MutableList<GuardedResult<ReadResult>> =
                mutableListOf(
                    GuardedResult(frameIsEmpty, PassLoadToNextFrame(loadData), null),
                    GuardedResult(continueReadOnNextFrame.not() and accept, nextFrame, value),
                    GuardedResult(continueReadOnNextFrame.not() and accept.not(), StepError(error), null),
                )

            if (passPartOfLoadDataFurther != null) {
                check(loadData.type is TvmCellDataBitArrayRead)
                result.add(
                    GuardedResult(
                        passPartOfLoadDataFurther.guard,
                        PassLoadToNextFrame(
                            LimitedLoadData(
                                loadData.cellAddress,
                                TvmCellDataBitArrayRead(passPartOfLoadDataFurther.leftBits).uncheckedCast(),
                            ),
                        ),
                        value = null,
                    ),
                )
            }

            result
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
}
