package org.usvm.machine.types.memory.stack

import io.ksmt.sort.KBvSort
import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.persistentListOf
import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.isFalse
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmStructuralError
import org.usvm.machine.state.allocSliceFromData
import org.usvm.machine.state.doWithCtx
import org.usvm.machine.types.TvmUnexpectedDataReading
import org.usvm.machine.types.UExprReadResult
import org.usvm.machine.types.isEmptyRead
import org.usvm.test.resolver.TvmTestStateResolver

data class TlbStack(
    private val frames: List<TlbStackFrame>,
    private val deepestError: TvmStructuralError? = null,
) {
    val isEmpty: Boolean
        get() = frames.isEmpty()

    fun <ReadResult> step(
        scope: TvmStepScopeManager,
        loadData: LimitedLoadData<ReadResult>,
        badCellSizeIsExceptional: Boolean,
        onBadCellSize: (TvmState, BadSizeContext) -> Unit,
    ): List<GuardedResult<ReadResult>>? =
        scope.doWithCtx {
            val ctx = scope.ctx
            val result = mutableListOf<GuardedResult<ReadResult>>()

            val emptyRead = loadData.type.isEmptyRead(ctx)

            if (frames.isEmpty()) {
                // finished parsing
                return@doWithCtx scope.calcOnState {
                    listOf(
                        GuardedResult(emptyRead, NewStack(this@TlbStack), value = null),
                        GuardedResult(
                            emptyRead.not(),
                            Error(
                                TvmStructuralError(TvmUnexpectedDataReading(loadData.type), phase, stack),
                                fromMutableTlb = false,
                            ),
                            value = null,
                        ),
                    )
                }
            }

            result.add(GuardedResult(emptyRead, NewStack(this@TlbStack), value = null))

            val lastFrame = frames.last()

            val frameSteps =
                lastFrame.step(scope, loadData, badCellSizeIsExceptional, onBadCellSize)
                    ?: return@doWithCtx null
            frameSteps.forEach { (guard, stackFrameStepResult, value) ->
                if (guard.isFalse) {
                    return@forEach
                }

                when (stackFrameStepResult) {
                    is EndOfStackFrame -> {
                        val newFrames =
                            calcOnState {
                                skipSingleStep(this, loadData.cellRef, frames.viewWithoutLast())
                            }
                        result.add(
                            GuardedResult(
                                guard and emptyRead.not(),
                                NewStack(TlbStack(newFrames, deepestError)),
                                value,
                            ),
                        )
                    }

                    is NextFrame -> {
                        val newStack =
                            TlbStack(
                                frames.viewWithoutLast() + stackFrameStepResult.frame,
                                deepestError,
                            )
                        result.add(
                            GuardedResult(
                                guard and emptyRead.not(),
                                NewStack(newStack, stackFrameStepResult.concreteLoaded),
                                value,
                            ),
                        )
                    }

                    is StepError -> {
                        check(value == null) {
                            "Extracting values from unsuccessful TL-B reads is not supported"
                        }

                        val nextLevelFrame = lastFrame.expandNewStackFrame(ctx)
                        if (nextLevelFrame != null) {
                            val newDeepestError = deepestError ?: stackFrameStepResult.error
                            val newStack =
                                TlbStack(
                                    frames + nextLevelFrame,
                                    newDeepestError,
                                )
                            val newStepResult =
                                newStack.step(scope, loadData, badCellSizeIsExceptional, onBadCellSize)
                                    ?: return@doWithCtx null
                            newStepResult.forEach { (innerGuard, stepResult, value) ->
                                val newGuard = ctx.mkAnd(guard, innerGuard)
                                result.add(GuardedResult(newGuard and emptyRead.not(), stepResult, value))
                            }
                        } else {
                            // condition [nextLevelFrame == null] means that we were about to parse TvmAtomicDataLabel

                            // condition [stackFrameStepResult.error == null] means that this TvmAtomicDataLabel
                            // was not builtin (situation A).

                            // condition [deepestError != null] means that there was an unsuccessful attempt to
                            // parse TvmCompositeDataLabel on a previous level (situation B).

                            val error = deepestError ?: stackFrameStepResult.error

                            // If A happened, B must have happened => [error] must be non-null
                            check(error != null) {
                                "Error was not set after unsuccessful TlbStack step."
                            }

                            val fromMutableTlb =
                                lastFrame is StackFrameOfUnknown || lastFrame.path.any { it == TlbStructure.Unknown.id }

                            result.add(
                                GuardedResult(guard and emptyRead.not(), Error(error, fromMutableTlb), value = null),
                            )
                        }
                    }

                    is ContinueLoadOnNextFrame<ReadResult> -> {
                        val newLoadData = stackFrameStepResult.loadData
                        val newFrames =
                            calcOnState {
                                skipSingleStep(this, loadData.cellRef, frames)
                            }
                        val newStack = TlbStack(newFrames, deepestError)
                        val stepResults =
                            newStack.step(scope, newLoadData, badCellSizeIsExceptional, onBadCellSize)
                                ?: return@doWithCtx null
                        stepResults.forEach { (innerGuard, stepResult, _) ->
                            // values from steps are discarded as we are only interested
                            // in concrete bitvector reads when reading values across multiple
                            // frames. These values are passed in stepResult
                            val newGuard = ctx.mkAnd(guard, innerGuard)
                            val accumulatedRight = (stepResult as? NewStack)?.accumulatedBv
                            val accumulatedLeft = stackFrameStepResult.concreteLoaded
                            val joinedConcreteValue =
                                if (accumulatedLeft != null && accumulatedRight != null) {
                                    mkBvConcatExpr(accumulatedLeft, accumulatedRight)
                                } else {
                                    null
                                }
                            val newStepResult =
                                if (stepResult is NewStack) {
                                    stepResult.copy(accumulatedBv = joinedConcreteValue)
                                } else {
                                    stepResult
                                }
                            result.add(
                                GuardedResult(
                                    newGuard and emptyRead.not(),
                                    newStepResult,
                                    value =
                                        joinedConcreteValue?.let {
                                            UExprReadResult(
                                                scope.calcOnState { allocSliceFromData(it) },
                                            ).uncheckedCast()
                                        },
                                ),
                            )
                        }
                    }
                }
            }

            result.removeAll { it.guard.isFalse }

            result
        }

    fun readInModel(readInfo: ConcreteReadInfo): Triple<String, ConcreteReadInfo, TlbStack> {
        require(frames.isNotEmpty())
        val lastFrame = frames.last()
        val (readValue, leftToRead, newFrames) = lastFrame.readInModel(readInfo)
        val deepFrames =
            if (newFrames.isEmpty()) {
                skipSingleStep(readInfo.resolver.state, readInfo.ref, frames.viewWithoutLast())
            } else {
                frames.viewWithoutLast()
            }
        val newTlbStack = TlbStack(deepFrames + newFrames)
        return Triple(readValue, leftToRead, newTlbStack)
    }

    fun compareWithOtherStack(
        scope: TvmStepScopeManager,
        cellRef: UConcreteHeapRef,
        otherStack: TlbStack,
        otherCellRef: UConcreteHeapRef,
    ): Pair<UBoolExpr?, Unit?> {
        if (frames.size != 1 || otherStack.frames.size != 1) {
            return null to Unit
        }

        val frame1 = frames.single()
        val frame2 = otherStack.frames.single()

        return frame1.compareWithOtherFrame(scope, cellRef, frame2, otherCellRef)
    }

    sealed interface StepResult

    data class Error(
        val error: TvmStructuralError,
        val fromMutableTlb: Boolean,
    ) : StepResult

    data class NewStack(
        val stack: TlbStack,
        val accumulatedBv: UExpr<KBvSort>? = null,
    ) : StepResult

    data class ConcreteReadInfo(
        val ref: UConcreteHeapRef,
        val resolver: TvmTestStateResolver,
        val leftBits: Int,
    )

    data class GuardedResult<ReadResult>(
        val guard: UBoolExpr,
        val result: StepResult,
        val value: ReadResult?,
    )

    companion object {
        fun new(
            ctx: TvmContext,
            label: TlbCompositeLabel,
        ): TlbStack {
            val struct = label.internalStructure
            val frame = buildFrameForStructure(ctx, struct, persistentListOf(), ctx.tvmOptions.tlbOptions.maxTlbDepth)
            val frames = frame?.let { listOf(it) } ?: emptyList()
            return TlbStack(frames)
        }

        /**
         *  Until we can skip the label at the top frame, pop the frame (possibly zero times).
         *  Then skip the label at the last frame and return the result.
         */
        private fun skipSingleStep(
            state: TvmState,
            ref: UConcreteHeapRef,
            framesToPop: List<TlbStackFrame>,
        ): List<TlbStackFrame> {
            if (framesToPop.isEmpty()) {
                return framesToPop
            }
            val prevFrame = framesToPop.last()
            return when (val newFrame = prevFrame.skipLabel(state, ref)) {
                TlbStackFrame.SkipNotPossible -> {
                    error("Cannot skip frame $prevFrame")
                }
                TlbStackFrame.EndOfFrame -> {
                    skipSingleStep(state, ref, framesToPop.viewWithoutLast())
                }
                is TlbStackFrame.NextFrame -> {
                    framesToPop.viewWithoutLast() + newFrame.frame
                }
            }
        }
    }
}

private fun <T> List<T>.viewWithoutLast() = subList(0, size - 1)
