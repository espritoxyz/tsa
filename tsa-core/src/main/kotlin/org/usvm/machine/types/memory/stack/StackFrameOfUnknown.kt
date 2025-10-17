package org.usvm.machine.types.memory.stack

import io.ksmt.expr.KBitVecValue
import kotlinx.collections.immutable.PersistentList
import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.ton.TlbStructureIdProvider
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.fields.TvmCellDataLengthFieldManager.Companion.UnknownBlockLengthField
import org.usvm.machine.state.calcOnStateCtx
import org.usvm.machine.types.memory.UnknownBlockField
import org.usvm.machine.types.memory.stack.TlbStackFrame.GuardedResult
import org.usvm.sizeSort

data class StackFrameOfUnknown(
    override val path: PersistentList<Int>,
    override val leftTlbDepth: Int,
    val hasOffset: Boolean,
) : TlbStackFrame {
    override fun <ReadResult> step(
        scope: TvmStepScopeManager,
        loadData: LimitedLoadData<ReadResult>,
    ): List<GuardedResult<ReadResult>> =
        scope.calcOnStateCtx {
            val inferenceManager = fieldManagers.cellDataFieldManager.inferenceManager
            val inferredStruct = inferenceManager.getInferredStruct(loadData.cellRef, path)

            if (inferredStruct != null) {
                check(!hasOffset) {
                    "Unexpected offset in StackFrameOfUnknown"
                }

                val nextFrame =
                    buildFrameForStructure(ctx, inferredStruct, path.add(TlbStructure.Unknown.id), leftTlbDepth)
                        ?: error("Unexpected null frame")

                return@calcOnStateCtx nextFrame.step(scope, loadData)
            }

            val defaultResult =
                listOf(
                    GuardedResult<ReadResult>(
                        ctx.trueExpr,
                        NextFrame(StackFrameOfUnknown(path, leftTlbDepth, hasOffset = true)),
                        value = null,
                    ),
                )

            if (hasOffset || inferenceManager.isFixated(loadData.cellRef)) {
                return@calcOnStateCtx defaultResult
            }

            val label =
                loadData.type.defaultTlbLabel()
                    ?: return@calcOnStateCtx defaultResult

            val newStructure =
                TlbStructure.KnownTypePrefix(
                    id = TlbStructureIdProvider.provideId(),
                    typeLabel = label,
                    typeArgIds = emptyList(),
                    owner = TlbStructure.UnknownStructOwner,
                    rest = TlbStructure.Unknown,
                )

            val newPath = path.add(TlbStructure.Unknown.id)

            val labelSize =
                loadData.type.defaultTlbLabelSize(this, loadData.cellRef, newPath.add(newStructure.id))
                    ?: error("Unexpected null defaultTlbLabelSize")

            val tlbFieldConstraint =
                if (label is TlbCompositeLabel) {
                    scope.calcOnState {
                        fieldManagers.cellDataFieldManager.addressToLabelMapper.calculatedTlbLabelInfo
                            .getTlbFieldConstraints(
                                this,
                                loadData.cellRef,
                                label,
                                newPath.add(newStructure.id),
                            )
                    }
                } else {
                    trueExpr
                }

            val restSizeField = UnknownBlockLengthField(newPath)
            val restSize = memory.readField(loadData.cellRef, restSizeField, restSizeField.getSort(ctx))

            val curSizeField = UnknownBlockLengthField(path)
            val curSize = memory.readField(loadData.cellRef, curSizeField, curSizeField.getSort(ctx))

            val sizeConstraint =
                mkBvAddExpr(labelSize, restSize.zeroExtendToSort(sizeSort)) eq curSize.zeroExtendToSort(sizeSort)

            val constraint = sizeConstraint and tlbFieldConstraint

            scope.checkSat(constraint)
                ?: return@calcOnStateCtx defaultResult

            scope.assert(constraint)
                ?: error("Unexpected solver result")

            inferenceManager.addInferredStruct(loadData.cellRef, path, newStructure)

            val nextFrame =
                buildFrameForStructure(ctx, newStructure, newPath, leftTlbDepth)
                    ?: error("Unexpected null frame")

            nextFrame.step(scope, loadData)
        }

    override fun expandNewStackFrame(ctx: TvmContext): TlbStackFrame? = null

    override val isSkippable: Boolean = false

    override fun skipLabel(ctx: TvmContext): TlbStackFrame? = null

    override fun readInModel(
        read: TlbStack.ConcreteReadInfo,
    ): Triple<String, TlbStack.ConcreteReadInfo, List<TlbStackFrame>> =
        with(read.resolver.state.ctx) {
            check(!hasOffset) {
                "Cannot read from StackFrameOfUnknown with offset"
            }

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
