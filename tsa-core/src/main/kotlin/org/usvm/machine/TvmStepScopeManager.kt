package org.usvm.machine

import org.ton.bytecode.TvmInst
import org.usvm.ForkCase
import org.usvm.StepResult
import org.usvm.UBoolExpr
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.TvmStepScopeManager.TvmStepScope.StepScopeState.CANNOT_BE_PROCESSED
import org.usvm.machine.TvmStepScopeManager.TvmStepScope.StepScopeState.CAN_BE_PROCESSED
import org.usvm.machine.TvmStepScopeManager.TvmStepScope.StepScopeState.DEAD
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.c2IsDefault
import org.usvm.machine.types.TvmType
import org.usvm.solver.USatResult
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult
import org.usvm.uctx

class TvmStepScopeManager(
    private val originalState: TvmState,
    private val forkBlackList: UForkBlackList<TvmState, TvmInst>,
    val allowFailuresOnCurrentStep: Boolean,
) {
    private val forkedStates = mutableListOf<TvmState>()
    private val scope: TvmStepScope = TvmStepScope(originalState, forkBlackList, forkedStates)

    var doNotKillScopeOnDoWithConditions: Boolean = false

    fun doWithState(block: TvmState.() -> Unit) = scope.doWithState(block)

    fun <T> calcOnState(block: TvmState.() -> T) = scope.calcOnState(block)

    val canBeProcessed
        get() = scope.canBeProcessed

    fun filterForkedStatesOnCondition(constraint: UBoolExpr) = scope.filterForkedStatesOnCondition(constraint)

    fun checkSat(condition: UBoolExpr) = scope.checkSat(condition)

    fun killForkedState(state: TvmState) {
        require(state != originalState) {
            "For killing current state, use killCurrentState()"
        }
        require(state in forkedStates) {
            "Unexpected state: it was not produced by current step scope."
        }

        forkedStates.remove(state)
    }

    fun assert(
        constraint: UBoolExpr,
        satBlock: TvmState.() -> Unit = {},
        unsatBlock: TvmState.() -> Unit = {},
        unknownBlock: TvmState.() -> Unit = {},
    ) = scope.assert(constraint, satBlock, unsatBlock, unknownBlock)

    val ctx: TvmContext
        get() = originalState.ctx

    fun stepResult(): StepResult<TvmState> {
        val originalStepResult = scope.stepResult()

        val originalStateAlive =
            originalStepResult.originalStateAlive &&
                (allowFailuresOnCurrentStep || !originalState.isExceptional)
        val resultingForkedStates =
            if (allowFailuresOnCurrentStep) {
                originalStepResult.forkedStates
            } else {
                originalStepResult.forkedStates.filter { !it.isExceptional }
            }
        return StepResult(resultingForkedStates, originalStateAlive)
    }

    fun fork(
        condition: UBoolExpr,
        falseStateIsExceptional: Boolean,
        blockOnTrueState: TvmState.() -> Unit = {},
        blockOnFalseState: TvmState.() -> Unit = {},
    ): Unit? {
        check(!originalState.isExceptional) {
            "Cannot fork on exceptional state"
        }

        if (falseStateIsExceptional && !allowFailuresOnCurrentStep && originalState.c2IsDefault()) {
            return assert(condition)?.run {
                originalState.blockOnTrueState()
            }
        }
        return scope.fork(
            condition,
            blockOnTrueState,
        ) {
            blockOnFalseState()

            // additional checks
            if (originalState.c2IsDefault()) {
                check(c2IsDefault()) {
                    "c2 register was changed in false state handler"
                }
                check(isExceptional == falseStateIsExceptional) {
                    "`falseStateIsExceptional` parameter in fork was wrong. " +
                        "Set value: $falseStateIsExceptional, but actual is $isExceptional"
                }
            }
        }
    }

    fun forkWithCheckerStatusKnowledge(
        condition: UBoolExpr,
        blockOnUnknownTrueState: TvmState.() -> Unit = {},
        blockOnUnsatTrueState: TvmState.() -> Unit = {},
        blockOnFalseState: TvmState.() -> Unit = {},
    ): Unit? {
        check(allowFailuresOnCurrentStep) {
            "[forkWithCheckerStatusKnowledge] should be called only with allowFailuresOnCurrentStep=true, but now it is false."
        }
        return scope.forkWithCheckerStatusKnowledge(
            condition,
            blockOnUnknownTrueState = blockOnUnknownTrueState,
            blockOnUnsatTrueState = blockOnUnsatTrueState,
            blockOnFalseState = blockOnFalseState,
        )
    }

    fun forkWithCheckerStatusKnowledgeOnFalseState(
        condition: UBoolExpr,
        blockOnUnknownFalseState: TvmState.() -> Unit = {},
        blockOnUnsatFalseState: TvmState.() -> Unit = {},
        blockOnFalseState: TvmState.() -> Unit = {},
    ): Unit? {
        check(allowFailuresOnCurrentStep) {
            "[forkWithCheckerStatusKnowledgeOnFalseState] should be called only with allowFailuresOnCurrentStep=true, but now it is false."
        }
        return scope.forkWithCheckerStatusKnowledge(
            condition,
            blockOnUnsatFalseState = blockOnUnsatFalseState,
            blockOnUnknownFalseState = blockOnUnknownFalseState,
            blockOnFalseState = blockOnFalseState,
        )
    }

    // TODO docs
    fun <T> doWithConditions(
        givenConditionsWithActions: List<ActionOnCondition<T>>,
        doForAllBlock: TvmStepScopeManager.(T) -> Unit,
    ) {
        check(!originalState.isExceptional) {
            "Cannot perform [doWithConditions] on exceptional state"
        }

        val conditionsWithActions =
            if (!allowFailuresOnCurrentStep && originalState.c2IsDefault()) {
                givenConditionsWithActions.filter { !it.caseIsExceptional }
            } else {
                givenConditionsWithActions
            }

        val states = ctx.statesForkProvider.forkMulti(originalState, conditionsWithActions.map { it.condition })
        if (!doNotKillScopeOnDoWithConditions) {
            scope.stepScopeState = CANNOT_BE_PROCESSED
        }
        states.forEachIndexed { idx, state ->
            state?.let {
                val action = conditionsWithActions[idx]
                action.action(state)

                if (originalState.c2IsDefault()) {
                    check(state.c2IsDefault()) {
                        "c2 register was changed in forked state handler"
                    }
                    check(state.isExceptional == action.caseIsExceptional) {
                        "action under condition ${action.condition} expected exceptional status:" +
                            " ${action.caseIsExceptional}, but actual one is: ${state.isExceptional}."
                    }
                }

                var stateAlive = true
                if (!state.isExceptional) {
                    val newScopeManager = TvmStepScopeManager(state, forkBlackList, allowFailuresOnCurrentStep)
                    newScopeManager.doForAllBlock(action.paramForDoForAllBlock)

                    val newScopeResults = newScopeManager.stepResult()
                    forkedStates += newScopeResults.forkedStates
                    stateAlive = newScopeResults.originalStateAlive

                    if (state == originalState) {
                        if (!doNotKillScopeOnDoWithConditions && !newScopeResults.originalStateAlive) {
                            scope.stepScopeState = DEAD
                        } else if (doNotKillScopeOnDoWithConditions) {
                            scope.stepScopeState = newScopeManager.scope.stepScopeState
                        }
                    }
                }

                if (state != originalState && stateAlive) {
                    forkedStates += state
                }
            }
        }
    }

    class ActionOnCondition<T>(
        val action: TvmState.() -> Unit,
        val caseIsExceptional: Boolean,
        val condition: UBoolExpr,
        val paramForDoForAllBlock: T,
    )

    /**
     * Modified version of usvm [StepScope] that distinguishes between UNSAT and UNKNOWN solver results at [assert].
     * UNSAT is an unexpected result, while UNKNOWN is not.
     *
     * An auxiliary class, which carefully maintains forks and asserts via [forkWithBlackList] and [assert].
     * It should be created on every step in an interpreter.
     * You can think about an instance of [TvmStepScope] as a monad `ExceptT null TvmState`.
     *
     * This scope is considered as [DEAD], iff the condition in [assert] was unsatisfiable or unknown.
     * The underlying state cannot be processed further (see [CANNOT_BE_PROCESSED]),
     * if the first passed to [forkWithBlackList] or [forkMultiWithBlackList] condition was unsatisfiable or unknown.
     *
     * To execute some function on a state, you should use [doWithState] or [calcOnState]. `null` is returned, when
     * this scope cannot be processed on the current step - see [CANNOT_BE_PROCESSED].
     *
     * @param originalState an initial state.
     */
    private class TvmStepScope(
        private val originalState: TvmState,
        private val forkBlackList: UForkBlackList<TvmState, TvmInst>,
        private val forkedStates: MutableList<TvmState> = mutableListOf(),
    ) {
        private inline val alive: Boolean get() = stepScopeState != DEAD
        private inline val canProcessFurtherOnCurrentStep: Boolean get() = stepScopeState == CAN_BE_PROCESSED
        private inline val ctx: TvmContext get() = originalState.ctx

        /**
         * Determines whether we interact this scope on the current step.
         * @see [StepScopeState].
         */
        internal var stepScopeState: StepScopeState = CAN_BE_PROCESSED

        /**
         * @return forked states and the status of initial state.
         */
        fun stepResult(): StepResult<TvmState> = StepResult(forkedStates.asSequence(), alive)

        inline val isDead: Boolean get() = stepScopeState === DEAD
        inline val canBeProcessed: Boolean get() = stepScopeState == CAN_BE_PROCESSED

        /**
         * Executes [block] on a state.
         */
        inline fun doWithState(block: TvmState.() -> Unit) {
            check(canProcessFurtherOnCurrentStep) { "Caller should check before processing the current hop further" }
            return originalState.block()
        }

        /**
         * Executes [block] on a state.
         */
        inline fun <R> calcOnState(block: TvmState.() -> R): R {
            check(canProcessFurtherOnCurrentStep) { "Using of an invalid scope" }
            return originalState.block()
        }

        /**
         * Forks on a [condition], performing [blockOnTrueState] on a state satisfying [condition] and
         * [blockOnFalseState] on a state satisfying [condition].not().
         *
         * If the [condition] is unsatisfiable or unknown, sets the scope state to the [CANNOT_BE_PROCESSED].
         *
         * @return `null` if the [condition] is unsatisfiable or unknown.
         */
        fun fork(
            condition: UBoolExpr,
            blockOnTrueState: TvmState.() -> Unit = {},
            blockOnFalseState: TvmState.() -> Unit = {},
        ): Unit? {
            check(canProcessFurtherOnCurrentStep)

            return forkWithCheckerStatusKnowledge(
                condition,
                blockOnTrueState = blockOnTrueState,
                blockOnFalseState = blockOnFalseState,
            )
        }

        fun forkWithCheckerStatusKnowledge(
            condition: UBoolExpr,
            blockOnUnknownTrueState: TvmState.() -> Unit = {},
            blockOnUnsatTrueState: TvmState.() -> Unit = {},
            blockOnTrueState: TvmState.() -> Unit = {},
            blockOnUnknownFalseState: TvmState.() -> Unit = {},
            blockOnUnsatFalseState: TvmState.() -> Unit = {},
            blockOnFalseState: TvmState.() -> Unit = {},
        ): Unit? {
            check(canProcessFurtherOnCurrentStep)

            val possibleForkPoint = originalState.pathNode

            val clonedState = originalState.clone()

            var unknownHappened = false

            assert(
                condition,
                unsatBlock = blockOnUnsatTrueState,
                unknownBlock = {
                    unknownHappened = true
                    blockOnUnknownTrueState()
                },
                satBlock = blockOnTrueState,
            ) ?: run {
                /**
                 * Hack: change [stepScopeState] to make assert with opposite constraint possible.
                 *
                 * If we got here, it means that [condition] was UNSAT or UNKNOWN,
                 * and that means that the original model in [originalState] did not satisfy [condition],
                 * and that means that asserting opposite constraint might result only in SAT.
                 * */
                stepScopeState = CAN_BE_PROCESSED
                assert(ctx.mkNot(condition), satBlock = blockOnFalseState)
                    ?: error("Unexpected assert result on negation of $condition")

                // fix current step scope
                stepScopeState = CANNOT_BE_PROCESSED

                // this is to avoid breaking iteration limit
                if (unknownHappened) {
                    originalState.forkPoints += possibleForkPoint
                }

                return null
            }

            val clonedStepScope = TvmStepScope(clonedState, forkBlackList)

            clonedStepScope.assert(
                ctx.mkNot(condition),
                satBlock = blockOnFalseState,
                unknownBlock = {
                    unknownHappened = true
                    blockOnUnknownFalseState()
                },
                unsatBlock = blockOnUnsatFalseState,
            )
            if (clonedStepScope.alive) {
                val newState = clonedStepScope.originalState
                forkedStates += newState
                newState.forkPoints += possibleForkPoint
                originalState.forkPoints += possibleForkPoint
            }

            if (unknownHappened) {
                originalState.forkPoints += possibleForkPoint
            }

            return Unit
        }

        /**
         * Forks on a few disjoint conditions using `forkMulti` in `State.kt`
         * and executes the corresponding block on each not-null state.
         *
         * NOTE: always sets the [stepScopeState] to the [CANNOT_BE_PROCESSED] value.
         */
        fun forkMulti(conditionsWithBlockOnStates: List<Pair<UBoolExpr, TvmState.() -> Unit>>) =
            forkMulti(conditionsWithBlockOnStates, skipForkPointIfPossible = true)

        /**
         * @param skipForkPointIfPossible determines whether it is allowed to skip fork point registration.
         * */
        private fun forkMulti(
            conditionsWithBlockOnStates: List<Pair<UBoolExpr, TvmState.() -> Unit>>,
            skipForkPointIfPossible: Boolean,
        ) {
            check(canProcessFurtherOnCurrentStep)

            val possibleForkPoint = originalState.pathNode

            val conditions = conditionsWithBlockOnStates.map { it.first }

            val conditionStates = ctx.statesForkProvider.forkMulti(originalState, conditions)

            val forkedStates =
                conditionStates.mapIndexedNotNull { idx, positiveState ->
                    val block = conditionsWithBlockOnStates[idx].second

                    positiveState?.apply(block)
                }

            stepScopeState = CANNOT_BE_PROCESSED
            if (forkedStates.isEmpty()) {
                stepScopeState = DEAD
                return
            }

            val firstForkedState = forkedStates.first()
            require(firstForkedState == originalState) {
                "The original state $originalState was expected to become the first of forked states but $firstForkedState found"
            }

            // Interpret the first state as original and others as forked
            this.forkedStates += forkedStates.subList(1, forkedStates.size)

            if (skipForkPointIfPossible && forkedStates.size < 2) return

            forkedStates.forEach { it.forkPoints += possibleForkPoint }
        }

        fun assert(
            constraint: UBoolExpr,
            satBlock: TvmState.() -> Unit = {},
            unsatBlock: TvmState.() -> Unit = {},
            unknownBlock: TvmState.() -> Unit = {},
        ): Unit? =
            assert(
                constraint,
                registerForkPoint = false,
                satBlock,
                unsatBlock,
                unknownBlock,
            )

        /**
         * @param registerForkPoint register a fork point if assert was successful.
         * */
        @Suppress("MoveVariableDeclarationIntoWhen")
        private fun assert(
            constraint: UBoolExpr,
            registerForkPoint: Boolean,
            satBlock: TvmState.() -> Unit = {},
            unsatBlock: TvmState.() -> Unit = {},
            unknownBlock: TvmState.() -> Unit = {},
        ): Unit? {
            check(canProcessFurtherOnCurrentStep)

            val possibleForkPoint = originalState.pathNode

            val trueModels = originalState.models.filter { it.eval(constraint).isTrue }

            if (trueModels.isNotEmpty()) {
                originalState.models = trueModels
            } else {
                val constraints = originalState.pathConstraints.clone()
                constraints += constraint

                val solver = originalState.ctx.solver<TvmType>()
                val solverResult = solver.check(constraints)

                when (solverResult) {
                    is USatResult -> {
                        originalState.models = listOf(solverResult.model)
                    }

                    is UUnsatResult -> {
                        originalState.unsatBlock()
                        stepScopeState = DEAD
                        return null
                    }

                    is UUnknownResult -> {
                        /**
                         * The following line was removed from here:
                         * "originalState.pathConstraints += constraint"
                         *
                         * That line invalidates [originalState], because after that we get
                         * a state with models that do not satisfy pathConstraints.
                         * Originally, that state was not used anymore, so that didn't make any effect.
                         *
                         * Now, [originalState] might be used even after this StepScope is dead.
                         * This happens in [forkWithCheckerStatusKnowledge].
                         * */
                        originalState.unknownBlock()
                        stepScopeState = DEAD
                        return null
                    }
                }
            }

            originalState.pathConstraints += constraint

            if (registerForkPoint) {
                originalState.forkPoints += possibleForkPoint
            }

            originalState.satBlock()

            return Unit
        }

        fun filterForkedStatesOnCondition(constraint: UBoolExpr) {
            val forkedStatesCopy = forkedStates.toList()
            forkedStatesCopy.forEach { forkedState ->
                val newStateScope = TvmStepScope(forkedState, forkBlackList)
                newStateScope.assert(constraint) ?: run {
                    forkedStates.remove(forkedState)
                }
            }
        }

        /**
         * [forkWithBlackList] version which doesn't fork to the branches with statements
         * banned by underlying [forkBlackList].
         *
         * @param trueStmt statement to fork on [condition].
         * @param falseStmt statement to fork on ![condition].
         */
        fun forkWithBlackList(
            condition: UBoolExpr,
            trueStmt: TvmInst,
            falseStmt: TvmInst,
            blockOnTrueState: TvmState.() -> Unit = {},
            blockOnFalseState: TvmState.() -> Unit = {},
        ): Unit? {
            check(canProcessFurtherOnCurrentStep)

            val shouldForkOnTrue = forkBlackList.shouldForkTo(originalState, trueStmt)
            val shouldForkOnFalse = forkBlackList.shouldForkTo(originalState, falseStmt)

            if (!shouldForkOnTrue && !shouldForkOnFalse) {
                stepScopeState = DEAD
                // TODO: should it be null?
                return null
            }

            if (shouldForkOnTrue && shouldForkOnFalse) {
                return fork(condition, blockOnTrueState, blockOnFalseState)
            }

            // If condition is concrete there is no fork point possibility
            val registerForkPoint = !condition.isConcrete

            if (shouldForkOnTrue) {
                return assert(condition, registerForkPoint, satBlock = blockOnTrueState)
            }

            return assert(condition.uctx.mkNot(condition), registerForkPoint, satBlock = blockOnFalseState)
        }

        /**
         * [forkMultiWithBlackList] version which doesn't fork to the branches with statements
         * banned by underlying [forkBlackList].
         */
        fun forkMultiWithBlackList(forkCases: List<ForkCase<TvmState, TvmInst>>) {
            check(canProcessFurtherOnCurrentStep)

            val filteredConditionsWithBlockOnStates =
                forkCases
                    .mapNotNull { case ->
                        if (!forkBlackList.shouldForkTo(originalState, case.stmt)) {
                            return@mapNotNull null
                        }
                        case.condition to case.block
                    }

            if (filteredConditionsWithBlockOnStates.isEmpty()) {
                stepScopeState = DEAD
                return
            }

            // If all conditions are concrete there is no fork point possibility
            val skipForkPoint = forkCases.all { it.condition.isConcrete }
            return forkMulti(filteredConditionsWithBlockOnStates, skipForkPoint)
        }

        /**
         * @return [Unit] if this [condition] is satisfiable, and returns `null` otherwise.
         */
        @Suppress("MoveVariableDeclarationIntoWhen")
        fun checkSat(condition: UBoolExpr): Unit? {
            check(canProcessFurtherOnCurrentStep)

            // If this state did not fork at all or was sat at the last fork point, it must be still sat, so we can just
            // check this condition with presented models
            if (originalState.models.isNotEmpty()) {
                val trueModels = originalState.models.filter { it.eval(condition).isTrue }

                if (trueModels.isNotEmpty()) {
                    return Unit
                }
            }

            val constraints = originalState.pathConstraints.clone()
            constraints += condition

            val solver = originalState.ctx.solver<TvmType>()
            val solverResult = solver.check(constraints)

            return when (solverResult) {
                is USatResult -> originalState.models += solverResult.model
                is UUnknownResult, is UUnsatResult -> null
            }
        }

        /**
         * Represents the current state of this [TvmStepScope].
         */
        enum class StepScopeState {
            /**
             * Cannot be processed further with any actions.
             */
            DEAD,

            /**
             * Cannot be forked or asserted using [forkWithBlackList], [forkMultiWithBlackList] or [assert],
             * but is considered as alive from the Machine's point of view.
             */
            CANNOT_BE_PROCESSED,

            /**
             * Can be forked using [forkWithBlackList] or [forkMultiWithBlackList] and asserted using [assert].
             */
            CAN_BE_PROCESSED,
        }

        private inline val UBoolExpr.isConcrete get() = isTrue || isFalse
    }
}
