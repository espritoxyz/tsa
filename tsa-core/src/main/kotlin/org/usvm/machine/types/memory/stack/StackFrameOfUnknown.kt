package org.usvm.machine.types.memory.stack

import io.ksmt.expr.KBitVecValue
import kotlinx.collections.immutable.PersistentList
import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.ton.TlbStructureIdProvider
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.isTrue
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.intValue
import org.usvm.machine.state.TvmState
import org.usvm.machine.types.InferredTlbLabel
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
    fun loadAndFixate(
        state: TvmState,
        cellRef: UConcreteHeapRef,
    ): UExpr<TvmContext.TvmCellDataSort>? {
        val inferenceManager = state.fieldManagers.cellDataFieldManager.inferenceManager
        if (hasOffset) {
            return null
        }
        inferenceManager.fixateRef(cellRef)

        val field = UnknownBlockField(TlbStructure.Unknown.id, path)
        val dataSymbolic =
            with(state) {
                memory.readField(cellRef, field, field.getSort(ctx))
            }

        return dataSymbolic
    }

    override fun <ReadResult> step(
        scope: TvmStepScopeManager,
        loadData: LimitedLoadData<ReadResult>,
        badCellSizeIsExceptional: Boolean,
        onBadCellSize: (TvmState, BadSizeContext) -> Unit,
    ): List<GuardedResult<ReadResult>>? =
        with(scope.ctx) {
            val ctx = scope.ctx
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

                return@with nextFrame.step(scope, loadData, badCellSizeIsExceptional, onBadCellSize)
            }

            val defaultResult =
                listOf(
                    GuardedResult<ReadResult>(
                        ctx.trueExpr,
                        NextFrame(StackFrameOfUnknown(path, leftTlbDepth, hasOffset = true)),
                        value = null,
                        doWhenForked = {},
                    ),
                )

            if (hasOffset || inferenceManager.isFixated(loadData.cellRef) || !loadData.guard.isTrue) {
                return@with defaultResult
            }

            val field = UnknownBlockField(TlbStructure.Unknown.id, path)
            val dataSymbolic =
                scope.calcOnState {
                    memory.readField(loadData.cellRef, field, field.getSort(ctx))
                }

            val labelVariants =
                loadData.type.defaultTlbLabel(dataSuffix = dataSymbolic)

            val assumeValue = labelVariants.fold(falseExpr as UBoolExpr) { a, b -> a or b.guard }

            scope.checkSat(assumeValue)
                ?: return@with defaultResult

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

            var forgottenConstraint = trueExpr as UBoolExpr

            if (scope.allowFailuresOnCurrentStep || !badCellSizeIsExceptional) {
                forgottenConstraint = assumeValue

                labelVariants.forEach { label ->
                    val sizeIsBad =
                        label.sizeIsBad(
                            dataSuffix = dataSymbolic,
                            dataSuffixLength = curSize,
                        )

                    var badSizeContext = BadSizeContext.GoodSizeIsSat

                    check(loadData.guard.isTrue) {
                        "Unexpected loadData guard"
                    }

                    forgottenConstraint = forgottenConstraint and (label.guard implies sizeIsBad.not())

                    if (scope.allowFailuresOnCurrentStep) {
                        scope.forkWithCheckerStatusKnowledge(
                            label.guard implies sizeIsBad.not(),
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
                        ) ?: return@with null
                    } else {
                        // case when [!badCellSizeIsExceptional]
                        scope.fork(
                            label.guard implies sizeIsBad.not(),
                            falseStateIsExceptional = false,
                            blockOnFalseState = {
                                // because UnknownBlockField got into constraints
                                fieldManagers.cellDataFieldManager.inferenceManager.fixateRef(loadData.cellRef)

                                // badSizeContext doesn't matter here
                                onBadCellSize(this, BadSizeContext.GoodSizeIsUnknown)
                            },
                        ) ?: return@with null
                    }
                }
            }

            return labelVariants
                .flatMap<InferredTlbLabel, GuardedResult<ReadResult>> { label ->
                    val newStructure =
                        when (label.struct) {
                            is InferredTlbLabel.TypeLabel ->
                                TlbStructure.KnownTypePrefix(
                                    id = TlbStructureIdProvider.provideId(),
                                    typeLabel = (label.struct as InferredTlbLabel.TypeLabel).label,
                                    typeArgIds = emptyList(),
                                    owner = TlbStructure.UnknownStructOwner,
                                    rest = TlbStructure.Unknown,
                                )
                            is InferredTlbLabel.Const ->
                                TlbStructure.SwitchPrefix(
                                    id = TlbStructureIdProvider.provideId(),
                                    switchSize = (label.struct as InferredTlbLabel.Const).value.length,
                                    givenVariants =
                                        mapOf(
                                            (label.struct as InferredTlbLabel.Const).value to TlbStructure.Unknown,
                                        ),
                                    owner = TlbStructure.UnknownStructOwner,
                                )
                        }

                    val newPath = path.add(TlbStructure.Unknown.id)

                    val labelSize =
                        label.labelSize(
                            scope.calcOnState { this },
                            loadData.cellRef,
                            newPath.add(newStructure.id),
                        )

                    val restSizeValue = mkSizeSubExpr(curSize, labelSize)

                    val tlbLabel = (label.struct as? InferredTlbLabel.TypeLabel)?.label as? TlbCompositeLabel
                    val tlbFieldConstraint =
                        if (tlbLabel != null) {
                            scope.calcOnState {
                                dataCellInfoStorage.mapper.calculatedTlbLabelInfo
                                    .getTlbFieldConstraints(
                                        this,
                                        loadData.cellRef,
                                        tlbLabel,
                                        newPath.add(newStructure.id),
                                    )
                            }
                        } else {
                            trueExpr
                        }

                    val tlbSizeConstraint = mkSizeGeExpr(restSizeValue, zeroSizeExpr)
                    val tlbConstraint = tlbSizeConstraint and tlbFieldConstraint

                    scope.checkSat(label.guard and tlbConstraint)
                        ?: return@flatMap emptyList()

                    val nextFrame =
                        buildFrameForStructure(ctx, newStructure, newPath, leftTlbDepth)
                            ?: error("Unexpected null frame")

                    check(nextFrame is KnownTypeTlbStackFrame || nextFrame is ConstTlbStackFrame) {
                        "Unexpected tlb frame: $nextFrame"
                    }

                    // we will need this only for [nextFrame.step], on the next iteration this might be rewritten
                    scope.doWithState {
                        label.writeToNextLabelFields(
                            this,
                            loadData.cellRef,
                            newPath,
                            newStructure.id,
                            dataSymbolic,
                        )
                    }
                    val nextFrameStepResult =
                        nextFrame.step(scope, loadData, badCellSizeIsExceptional, onBadCellSize)
                            ?: return null

                    nextFrameStepResult.map { res ->
                        GuardedResult(
                            guard = label.guard and tlbConstraint and res.guard,
                            result = res.result,
                            value = res.value,
                        ) {
                            val restSizeField = UnknownBlockLengthField(newPath)
                            it.memory.writeField(
                                loadData.cellRef,
                                restSizeField,
                                restSizeField.getSort(ctx),
                                restSizeValue,
                                guard = trueExpr,
                            )

                            it.fieldManagers.cellDataFieldManager.inferenceManager.addInferredStruct(
                                loadData.cellRef,
                                path,
                                newStructure,
                            )

                            // restore the value
                            label.writeToNextLabelFields(
                                it,
                                loadData.cellRef,
                                newPath,
                                newStructure.id,
                                dataSymbolic,
                            )

                            res.doWhenForked(it)
                        }
                    }
                }.ifEmpty {
                    scope.assert(forgottenConstraint)
                        ?: return@with null

                    inferenceManager.fixateRef(loadData.cellRef) // because UnknownBlockField got into constraints

                    return@with defaultResult
                }
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

    override fun readInModel(read: TlbStack.ConcreteReadInfo): TlbStackFrame.ModelReadResult =
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
                val dataConcrete = read.resolver.model.eval(dataSymbolic)
                val data = (dataConcrete as KBitVecValue<*>).stringValue

                val restSizeField = UnknownBlockLengthField(path)
                val restSize =
                    read.resolver.state.memory
                        .readField(
                            read.ref,
                            restSizeField,
                            restSizeField.getSort(read.ref.ctx.tctx()),
                        ).zeroExtendToSort(sizeSort)
                val concreteRestSize = read.resolver.eval(restSize)

                val sizeGuard = restSize eq concreteRestSize

                val high = dataSymbolic.sort.sizeBits.toInt() - 1
                val low = dataSymbolic.sort.sizeBits.toInt() - read.leftBits
                val dataGuard =
                    if (low <= high) {
                        mkBvExtractExpr(high, low, dataSymbolic) eq mkBvExtractExpr(high, low, dataConcrete)
                    } else {
                        trueExpr
                    }

                check(concreteRestSize.intValue() == read.leftBits) {
                    "Unexpected read in StackFrameOfUnknown"
                }

                val newReadInfo = TlbStack.ConcreteReadInfo(read.ref, read.resolver, leftBits = 0)

                val guard = sizeGuard and dataGuard

                TlbStackFrame.ModelReadResult(
                    data.take(read.leftBits),
                    newReadInfo,
                    emptyList(),
                    guard,
                    missedSlices = emptyList(),
                )
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
