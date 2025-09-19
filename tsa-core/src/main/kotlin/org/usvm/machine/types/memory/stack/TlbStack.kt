package org.usvm.machine.types.memory.stack

import kotlinx.collections.immutable.persistentListOf
import org.ton.TlbCompositeLabel
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.isFalse
import org.usvm.logger
import org.usvm.machine.TvmContext
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmStructuralError
import org.usvm.machine.types.TvmCellDataTypeReadValue
import org.usvm.machine.types.TvmUnexpectedDataReading
import org.usvm.machine.types.isEmptyRead
import org.usvm.test.resolver.TvmTestStateResolver

data class TlbStack(
    private val frames: List<TlbStackFrame>,
    private val deepestError: TvmStructuralError? = null,
) {
    val isEmpty: Boolean
        get() = frames.isEmpty()

    fun <ReadResult : TvmCellDataTypeReadValue> step(
        state: TvmState,
        loadData: LimitedLoadData<ReadResult>,
    ): List<GuardedResult<ReadResult>> =
        with(state.ctx) {
            val ctx = state.ctx
            val result = mutableListOf<GuardedResult<ReadResult>>()

            val emptyRead = loadData.type.isEmptyRead(ctx)

            if (frames.isEmpty()) {
                // finished parsing
                return listOf(
                    GuardedResult(emptyRead, NewStack(this@TlbStack), value = null),
                    GuardedResult(
                        emptyRead.not(),
                        Error(TvmStructuralError(TvmUnexpectedDataReading(loadData.type), state.phase, state.stack)),
                        value = null,
                    ),
                )
            }

            result.add(GuardedResult(emptyRead, NewStack(this@TlbStack), value = null))

            val lastFrame = frames.last()

            val frameSteps = lastFrame.step(state, loadData)
            frameSteps.forEach { (guard, stackFrameStepResult, value) ->
                if (guard.isFalse) {
                    return@forEach
                }

                when (stackFrameStepResult) {
                    is EndOfStackFrame -> {
                        val newFrames = skipSingleLabel(ctx, frames.viewWithoutLast())
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
                                NewStack(newStack),
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
                            newStack.step(state, loadData).forEach { (innerGuard, stepResult, value) ->
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

                            result.add(GuardedResult(guard and emptyRead.not(), Error(error), value = null))
                        }
                    }

                    is PassLoadToNextFrame<ReadResult> -> {
                        if (value != null) {
                            logger.warn("Extracting values in partial reads is not supported")
                        }
                        val newLoadData = stackFrameStepResult.loadData
                        val newFrames = skipSingleLabel(ctx, frames)
                        val newStack = TlbStack(newFrames, deepestError)

                        newStack.step(state, newLoadData).forEach { (innerGuard, stepResult) ->
                            val newGuard = ctx.mkAnd(guard, innerGuard)
                            // TODO: values for PassLoadToNextFrame
                            result.add(GuardedResult(newGuard and emptyRead.not(), stepResult, value = null))
                        }
                    }
                }
            }

            result.removeAll { it.guard.isFalse }

            return result
        }

    fun readInModel(readInfo: ConcreteReadInfo): Triple<String, ConcreteReadInfo, TlbStack> {
        require(frames.isNotEmpty())
        val lastFrame = frames.last()
        val (readValue, leftToRead, newFrames) = lastFrame.readInModel(readInfo)
        val deepFrames =
            if (newFrames.isEmpty()) {
                skipSingleLabel(readInfo.resolver.state.ctx, frames.viewWithoutLast())
            } else {
                frames.viewWithoutLast()
            }
        val newTlbStack = TlbStack(deepFrames + newFrames)
        return Triple(readValue, leftToRead, newTlbStack)
    }

    sealed interface StepResult

    data class Error(
        val error: TvmStructuralError,
    ) : StepResult

    data class NewStack(
        val stack: TlbStack,
    ) : StepResult

    data class ConcreteReadInfo(
        val address: UConcreteHeapRef,
        val resolver: TvmTestStateResolver,
        val leftBits: Int,
    )

    data class GuardedResult<ReadResult : TvmCellDataTypeReadValue>(
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
         *  Then skip the label at the last fram and return the result.
         */
        private fun skipSingleLabel(
            ctx: TvmContext,
            framesToPop: List<TlbStackFrame>,
        ): List<TlbStackFrame> {
            if (framesToPop.isEmpty()) {
                return framesToPop
            }
            val prevFrame = framesToPop.last()
            check(prevFrame.isSkippable) {
                "$prevFrame must be skippable, but it is not"
            }
            val newFrame = prevFrame.skipLabel(ctx)
            return if (newFrame == null) {
                skipSingleLabel(ctx, framesToPop.viewWithoutLast())
            } else {
                framesToPop.viewWithoutLast() + newFrame
            }
        }
    }
}

private fun <T> List<T>.viewWithoutLast() = subList(0, size - 1)
