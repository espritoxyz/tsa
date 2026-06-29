package org.usvm.machine.ps

import org.ton.bytecode.TvmCodeBlock
import org.ton.bytecode.TvmRealInst
import org.usvm.PathNode
import org.usvm.UPathSelector
import org.usvm.machine.ConcreteOpcode
import org.usvm.machine.ExcludedOpcodes
import org.usvm.machine.FollowTrace
import org.usvm.machine.NoOpcode
import org.usvm.machine.OpcodeInfo
import org.usvm.machine.TvmLoopTracker
import org.usvm.machine.TvmOptions
import org.usvm.machine.TvmPathSelectionStrategy
import org.usvm.machine.state.TvmState
import org.usvm.ps.BfsPathSelector
import org.usvm.ps.ConstantTimeFairPathSelector
import org.usvm.ps.DfsPathSelector
import org.usvm.ps.IterativeDeepeningPs
import org.usvm.ps.LoopLimiterPs
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.UMachineObserver
import org.usvm.util.RealTimeStopwatch
import kotlin.time.Duration

data class PSCreationContext(
    val options: TvmOptions,
    val timeStatistics: TimeStatistics<TvmCodeBlock, TvmState>,
    val loopTracker: TvmLoopTracker,
) {
    var psObserver: UMachineObserver<TvmState>? = null
}

fun createPathSelector(
    ctx: PSCreationContext,
    getInitialState: (OpcodeInfo?) -> TvmState,
): UPathSelector<TvmState> =
    with(ctx) {
        fun getRemainingTimeMs(): Duration {
            val diff = options.timeout - timeStatistics.runningTime
            return diff.coerceAtLeast(Duration.ZERO)
        }

        val rawPathSelector =
            if (options.divideTimeBetweenOpcodes == null) {
                val initialState = getInitialState(null)
                createPathSelectorLevel0(initialState, this)
            } else {
                check(!options.addTimeoutIfNotSatiated) {
                    "Cannot use this with path selection between opcodes"
                }

                val keys =
                    options.divideTimeBetweenOpcodes.opcodes.map { ConcreteOpcode(it) } +
                        ExcludedOpcodes(options.divideTimeBetweenOpcodes.opcodes) + NoOpcode

                ConstantTimeFairPathSelector(
                    initialKeys = keys.toSet(),
                    stopwatch = RealTimeStopwatch(),
                    ::getRemainingTimeMs,
                    getKey = {
                        it.additionalInputsConcreteData[options.divideTimeBetweenOpcodes.inputId]?.opcodeInfo
                            ?: error(
                                "Concrete info about input ${options.divideTimeBetweenOpcodes.inputId} not found",
                            )
                    },
                    getKeyPriority = { 0 },
                    basePathSelectorFactory = {
                        val initialState = getInitialState(it)

                        createPathSelectorLevel0(initialState, this)
                    },
                )
            }

        val pathSelector =
            options.followTrace?.let {
                check(!options.addTimeoutIfNotSatiated) {
                    "Cannot use when following trace"
                }
                wrapPathSelectorToChooseSpecificTrace(rawPathSelector, it)
            } ?: rawPathSelector

        return pathSelector
    }

private fun wrapPathSelectorToChooseSpecificTrace(
    pathSelector: UPathSelector<TvmState>,
    trace: FollowTrace,
): UPathSelector<TvmState> {
    return object : UPathSelector<TvmState> {
        override fun isEmpty(): Boolean = pathSelector.isEmpty()

        override fun peek(): TvmState = pathSelector.peek()

        override fun remove(state: TvmState) = pathSelector.remove(state)

        override fun add(states: Collection<TvmState>) = states.forEach(::addOne)

        private fun takeState(state: TvmState): Boolean {
            val curTrace =
                state.pathNode.allStatements
                    .toList()
                    .asReversed()
                    .mapNotNull { inst ->
                        (inst as? TvmRealInst)?.physicalLocation?.let {
                            it.cellHashHex to
                                it.offset
                        }
                    }
            return trace.locations.take(curTrace.size) == curTrace
        }

        private fun addOne(state: TvmState) {
            if (takeState(state)) {
                pathSelector.add(listOf(state))
            }
        }

        override fun update(state: TvmState) {
            if (takeState(state)) {
                pathSelector.update(state)
            } else {
                pathSelector.remove(state)
            }
        }
    }
}

private fun createPathSelectorLevel0(
    initialState: TvmState,
    ctx: PSCreationContext,
): UPathSelector<TvmState> {
    val ps = createPathSelectorLevel1(ctx)
    val result =
        if (ps !is IterativeDeepeningPs<*, *, *, *> && ctx.options.loopIterationLimit != null) {
            LoopLimiterPs(ps, ctx.loopTracker, ctx.options.loopIterationLimit - 1)
        } else {
            ps
        }
    result.add(listOf(initialState))
    return result
}

private fun createPathSelectorLevel1(ctx: PSCreationContext): UPathSelector<TvmState> =
    when (ctx.options.pathSelectionStrategy) {
        TvmPathSelectionStrategy.DFS_BASED -> createShakingPathSelector(ctx)
        TvmPathSelectionStrategy.BFS -> addIterativeDeepening(ctx.options, BfsPathSelector(), ctx.loopTracker)
    }

private fun createShakingPathSelector(ctx: PSCreationContext): TvmSeedBasedPathSelector {
    val strategy =
        if (ctx.options.addTimeoutIfNotSatiated) {
            TvmCompositeSeedStrategy(
                listOf(
                    TvmUncoveredInstSeedStrategy(),
                    TvmRandomTreeSeedStrategy(PathNode.root()),
                ),
            )
        } else {
            TvmRandomTreeSeedStrategy(PathNode.root())
        }

    ctx.psObserver = strategy.getObserver()

    return TvmSeedBasedPathSelector(
        strategy,
        ctx.timeStatistics,
        currentTimeout = ctx.options.timeout,
        timeStep = if (ctx.options.addTimeoutIfNotSatiated) ctx.options.timeout else null,
    ) {
        addIterativeDeepening(ctx.options, DfsPathSelector(), ctx.loopTracker)
    }
}

private fun addIterativeDeepening(
    options: TvmOptions,
    ps: UPathSelector<TvmState>,
    loopTracker: TvmLoopTracker,
): UPathSelector<TvmState> {
    val loopIterationLimit = options.loopIterationLimit?.let { it - 1 }
    return IterativeDeepeningPs(ps, loopTracker, loopIterationLimit)
}
