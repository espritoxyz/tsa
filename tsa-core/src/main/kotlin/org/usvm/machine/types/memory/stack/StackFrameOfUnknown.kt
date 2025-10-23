package org.usvm.machine.types.memory.stack

import io.ksmt.expr.KBitVecValue
import kotlinx.collections.immutable.PersistentList
import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.ton.TlbStructureIdProvider
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.isTrue
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.intValue
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.doWithCtx
import org.usvm.machine.types.memory.UnknownBlockField
import org.usvm.machine.types.memory.UnknownBlockLengthField
import org.usvm.machine.types.memory.stack.TlbStackFrame.GuardedResult
import org.usvm.mkSizeGeExpr
import org.usvm.mkSizeSubExpr
import org.usvm.sizeSort

data class StackFrameOfUnknown(
    override val path: PersistentList<Int>,
    override val leftTlbDepth: Int,
    val hasOffset: Boolean,
) : TlbStackFrame {
    override fun <ReadResult> step(
        scope: TvmStepScopeManager,
        loadData: LimitedLoadData<ReadResult>,
        badCellSizeIsExceptional: Boolean,
        onBadCellSize: (TvmState, BadSizeContext) -> Unit,
    ): List<GuardedResult<ReadResult>>? =
        scope.doWithCtx {
            val inferenceManager =
                scope.calcOnState {
                    fieldManagers.cellDataFieldManager.inferenceManager
                }
            val inferredStruct = inferenceManager.getInferredStruct(loadData.cellRef, path)

            if (inferredStruct != null) {
                check(!hasOffset) {
                    "Unexpected offset in StackFrameOfUnknown"
                }

                val nextFrame =
                    buildFrameForStructure(ctx, inferredStruct, path.add(TlbStructure.Unknown.id), leftTlbDepth)
                        ?: error("Unexpected null frame")

                return@doWithCtx nextFrame.step(scope, loadData, badCellSizeIsExceptional, onBadCellSize)
            }

            val defaultResult =
                listOf(
                    GuardedResult<ReadResult>(
                        ctx.trueExpr,
                        NextFrame(StackFrameOfUnknown(path, leftTlbDepth, hasOffset = true)),
                        value = null,
                    ),
                )

            if (hasOffset || inferenceManager.isFixated(loadData.cellRef) || !loadData.guard.isTrue) {
                return@doWithCtx defaultResult
            }

            val label =
                loadData.type.defaultTlbLabel()
                    ?: return@doWithCtx defaultResult

            val curSizeField = UnknownBlockLengthField(path)
            val curSize =
                scope.calcOnState {
                    memory
                        .readField(
                            loadData.cellRef,
                            curSizeField,
                            curSizeField.getSort(ctx),
                        ).zeroExtendToSort(sizeSort)
                }

            val field = UnknownBlockField(TlbStructure.Unknown.id, path)
            val dataSymbolic =
                scope.calcOnState {
                    memory.readField(loadData.cellRef, field, field.getSort(ctx))
                }

            var forgottenConstraint = trueExpr as UBoolExpr

            if (scope.allowFailuresOnCurrentStep) {
                val (sizeIsBad, assumeValue) =
                    loadData.type.sizeIsBad(
                        dataSuffix = dataSymbolic,
                        dataSuffixLength = curSize,
                    )

                scope.checkSat(assumeValue)
                    ?: return@doWithCtx defaultResult

                var badSizeContext = BadSizeContext.GoodSizeIsSat

                check(loadData.guard.isTrue) {
                    "Unexpected loadData guard"
                }

                forgottenConstraint = sizeIsBad.not() and assumeValue

                scope.forkWithCheckerStatusKnowledge(
                    assumeValue implies sizeIsBad.not(),
                    blockOnUnknownTrueState = {
                        badSizeContext = BadSizeContext.GoodSizeIsUnknown
                    },
                    blockOnUnsatTrueState = {
                        badSizeContext = BadSizeContext.GoodSizeIsUnsat
                    },
                    blockOnFalseState = {
                        // because UnknownBlockField got into constraints
                        fieldManagers.cellDataFieldManager.inferenceManager.fixateRef(loadData.cellRef)

                        onBadCellSize(this, badSizeContext)
                    },
                    doNotAddConstraintToTrueState = true, // because further constraints are stronger
                ) ?: return@doWithCtx null
            }

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
                loadData.type.defaultTlbLabelSize(
                    scope.calcOnState { this },
                    loadData.cellRef,
                    newPath.add(newStructure.id),
                )
                    ?: error("Unexpected null defaultTlbLabelSize")

            val restSizeValue = mkSizeSubExpr(curSize, labelSize)
            val restSizeField = UnknownBlockLengthField(newPath)

            // if we don't use this scheme after all, we will just ignore this field
            scope.doWithState {
                memory.writeField(
                    loadData.cellRef,
                    restSizeField,
                    restSizeField.getSort(ctx),
                    restSizeValue,
                    guard = trueExpr,
                )
            }

            val tlbFieldConstraint =
                if (label is TlbCompositeLabel) {
                    scope.calcOnState {
                        dataCellInfoStorage.mapper.calculatedTlbLabelInfo
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

            val tlbSizeConstraint = mkSizeGeExpr(restSizeValue, zeroSizeExpr)
            val tlbConstraint = tlbSizeConstraint and tlbFieldConstraint

            scope.checkSat(tlbConstraint)
                ?: run {
                    scope.assert(forgottenConstraint)
                        ?: return@doWithCtx null

                    inferenceManager.fixateRef(loadData.cellRef) // because UnknownBlockField got into constraints

                    return@doWithCtx defaultResult
                }

            scope.assert(tlbConstraint)
                ?: error("Unexpected solver result")

            inferenceManager.addInferredStruct(loadData.cellRef, path, newStructure)

            loadData.type.writeToNextLabelFields(
                scope.calcOnState {
                    this
                },
                loadData.cellRef,
                newPath,
                newStructure.id,
                dataSymbolic,
            )

            val nextFrame =
                buildFrameForStructure(ctx, newStructure, newPath, leftTlbDepth)
                    ?: error("Unexpected null frame")

            nextFrame.step(scope, loadData, badCellSizeIsExceptional, onBadCellSize)
        }

    override fun expandNewStackFrame(ctx: TvmContext): TlbStackFrame? = null

    override fun skipLabel(
        state: TvmState,
        ref: UConcreteHeapRef,
    ): TlbStackFrame.SkipResult {
        val inferenceManager = state.fieldManagers.cellDataFieldManager.inferenceManager

        val inferredStruct =
            inferenceManager.getInferredStruct(ref, path)
                ?: return TlbStackFrame.SkipNotPossible

        val nextFrame =
            buildFrameForStructure(state.ctx, inferredStruct, path.add(TlbStructure.Unknown.id), leftTlbDepth)
                ?: error("Unexpected null frame")

        return nextFrame.skipLabel(state, ref)
    }

    override fun readInModel(
        read: TlbStack.ConcreteReadInfo,
    ): Triple<String, TlbStack.ConcreteReadInfo, List<TlbStackFrame>> =
        with(read.resolver.state.ctx) {
            check(!hasOffset) {
                "Cannot read from StackFrameOfUnknown with offset"
            }

            val inferredStruct =
                read.resolver.state.fieldManagers.cellDataFieldManager.inferenceManager.getInferredStruct(
                    read.ref,
                    path,
                )

            if (inferredStruct == null) {
                val field = UnknownBlockField(TlbStructure.Unknown.id, path)
                val dataSymbolic =
                    read.resolver.state.memory
                        .readField(read.ref, field, field.getSort(this))
                val data = (read.resolver.model.eval(dataSymbolic) as KBitVecValue<*>).stringValue

                val restSizeField = UnknownBlockLengthField(path)
                val restSize =
                    read.resolver.state.memory
                        .readField(
                            read.ref,
                            restSizeField,
                            restSizeField.getSort(read.ref.ctx.tctx()),
                        ).zeroExtendToSort(sizeSort)
                val concreteRestSize = read.resolver.eval(restSize).intValue()

                check(concreteRestSize == read.leftBits) {
                    "Unexpected read in StackFrameOfUnknown"
                }

                val newReadInfo = TlbStack.ConcreteReadInfo(read.ref, read.resolver, leftBits = 0)

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
