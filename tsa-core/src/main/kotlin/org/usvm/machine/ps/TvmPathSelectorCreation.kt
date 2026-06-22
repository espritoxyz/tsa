package org.usvm.machine.ps

import org.ton.bytecode.TvmCodeBlock
import org.ton.bytecode.TvmRealInst
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
import org.usvm.ps.IterativeDeepeningPs
import org.usvm.statistics.TimeStatistics
import org.usvm.util.RealTimeStopwatch
import kotlin.time.Duration

data class PSCreationContext(
    val options: TvmOptions,
    val observer: TvmTreeShakerPathSelector.Observer,
    val timeStatistics: TimeStatistics<TvmCodeBlock, TvmState>,
)

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
                createPathSelectorLevel1(initialState, this)
            } else {
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

                        createPathSelectorLevel1(initialState, this)
                    },
                )
            }

        val pathSelector =
            options.followTrace?.let { wrapPathSelectorToChooseSpecificTrace(rawPathSelector, it) }
                ?: rawPathSelector

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

private fun createPathSelectorLevel1(
    initialState: TvmState,
    ctx: PSCreationContext,
): UPathSelector<TvmState> =
    if (ctx.options.groupStatesByOutMessages) {
        TvmOutOpcodePathSelector {
            createPathSelectorLevel2(it, ctx)
        }.also {
            it.add(initialState)
        }
    } else {
        createPathSelectorLevel2(initialState, ctx)
    }

private fun createPathSelectorLevel2(
    initialState: TvmState,
    ctx: PSCreationContext,
): UPathSelector<TvmState> {
    val ps = createPathSelectorLevel3(initialState, ctx)
    val loopTracker = TvmLoopTracker()
    val loopIterationLimit = ctx.options.loopIterationLimit?.let { it - 1 }
    return IterativeDeepeningPs(ps, loopTracker, loopIterationLimit).also {
        it.add(listOf(initialState))
    }
}

private fun createPathSelectorLevel3(
    initialState: TvmState,
    ctx: PSCreationContext,
): UPathSelector<TvmState> =
    when (ctx.options.pathSelectionStrategy) {
        TvmPathSelectionStrategy.BFS -> {
            BfsPathSelector<TvmState>().also {
                it.add(listOf(initialState))
            }
        }
        TvmPathSelectionStrategy.DFS_BASED -> {
            TvmTreeShakerPathSelector(initialState, ctx.observer)
        }
    }
