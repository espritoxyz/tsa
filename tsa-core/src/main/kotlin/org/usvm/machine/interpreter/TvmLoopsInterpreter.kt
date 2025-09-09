package org.usvm.machine.interpreter

import org.ton.bytecode.TvmAgainContinuation
import org.ton.bytecode.TvmContLoopsAgainInst
import org.ton.bytecode.TvmContLoopsAgainbrkInst
import org.ton.bytecode.TvmContLoopsAgainendInst
import org.ton.bytecode.TvmContLoopsAgainendbrkInst
import org.ton.bytecode.TvmContLoopsInst
import org.ton.bytecode.TvmContLoopsRepeatInst
import org.ton.bytecode.TvmContLoopsRepeatbrkInst
import org.ton.bytecode.TvmContLoopsRepeatendInst
import org.ton.bytecode.TvmContLoopsRepeatendbrkInst
import org.ton.bytecode.TvmContLoopsUntilInst
import org.ton.bytecode.TvmContLoopsUntilbrkInst
import org.ton.bytecode.TvmContLoopsUntilendInst
import org.ton.bytecode.TvmContLoopsUntilendbrkInst
import org.ton.bytecode.TvmContLoopsWhileInst
import org.ton.bytecode.TvmContLoopsWhilebrkInst
import org.ton.bytecode.TvmContLoopsWhileendInst
import org.ton.bytecode.TvmContLoopsWhileendbrkInst
import org.ton.bytecode.TvmContinuation
import org.ton.bytecode.TvmInstLocation
import org.ton.bytecode.TvmLoopEntranceContinuation
import org.ton.bytecode.TvmRepeatContinuation
import org.ton.bytecode.TvmUntilContinuation
import org.ton.bytecode.TvmWhileContinuation
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.C0Register
import org.usvm.machine.state.C1Register
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.defineC0
import org.usvm.machine.state.defineC1
import org.usvm.machine.state.extractCurrentContinuation
import org.usvm.machine.state.jumpToContinuation
import org.usvm.machine.state.signedIntegerFitsBits
import org.usvm.machine.state.takeLastContinuation
import org.usvm.machine.state.takeLastIntOrThrowTypeError

class TvmLoopsInterpreter(
    private val ctx: TvmContext,
) {
    private var loopIdx = 0u

    fun visitTvmContLoopsInst(
        scope: TvmStepScopeManager,
        stmt: TvmContLoopsInst,
    ) {
        scope.consumeDefaultGas(stmt)

        when (stmt) {
            is TvmContLoopsRepeatInst -> visitRepeatInst(scope, stmt, hasBreak = false)
            is TvmContLoopsRepeatendInst -> visitRepeatEndInst(scope, stmt, hasBreak = false)
            is TvmContLoopsRepeatbrkInst -> visitRepeatInst(scope, stmt, hasBreak = true)
            is TvmContLoopsRepeatendbrkInst -> visitRepeatEndInst(scope, stmt, hasBreak = true)
            is TvmContLoopsUntilInst -> visitUntilInst(scope, stmt, hasBreak = false)
            is TvmContLoopsUntilendInst -> visitUntilEndInst(scope, stmt, hasBreak = false)
            is TvmContLoopsUntilbrkInst -> visitUntilInst(scope, stmt, hasBreak = true)
            is TvmContLoopsUntilendbrkInst -> visitUntilEndInst(scope, stmt, hasBreak = true)
            is TvmContLoopsWhileInst -> visitWhileInst(scope, stmt, hasBreak = false)
            is TvmContLoopsWhileendInst -> visitWhileEndInst(scope, stmt, hasBreak = false)
            is TvmContLoopsWhilebrkInst -> visitWhileInst(scope, stmt, hasBreak = true)
            is TvmContLoopsWhileendbrkInst -> visitWhileEndInst(scope, stmt, hasBreak = true)
            is TvmContLoopsAgainInst -> visitAgainInst(scope, stmt, hasBreak = false)
            is TvmContLoopsAgainendInst -> visitAgainEndInst(scope, stmt, hasBreak = false)
            is TvmContLoopsAgainbrkInst -> visitAgainInst(scope, stmt, hasBreak = true)
            is TvmContLoopsAgainendbrkInst -> visitAgainEndInst(scope, stmt, hasBreak = true)
        }
    }

    /**
     * Registers a loop breakpoint by setting the c1 register
     */
    private fun TvmState.registerBreakpoint(
        cont: TvmContinuation,
        hasBreak: Boolean,
    ): TvmContinuation {
        if (!hasBreak) {
            return cont
        }

        val registers = registersOfCurrentContract
        val newCont = cont.defineC0(registers.c0.value).defineC1(registers.c1.value)

        registers.c1 = C1Register(newCont)

        return newCont
    }

    private fun visitRepeatInst(
        scope: TvmStepScopeManager,
        stmt: TvmContLoopsInst,
        hasBreak: Boolean,
    ) = doRepeat(
        scope = scope,
        extractBody = { stack.takeLastContinuation() },
        extractAfter = { extractCurrentContinuation(stmt, saveC0 = true) },
        hasBreak = hasBreak,
        parentLocation = stmt.location
    )

    private fun visitRepeatEndInst(
        scope: TvmStepScopeManager,
        stmt: TvmContLoopsInst,
        hasBreak: Boolean,
    ) = doRepeat(
        scope = scope,
        extractBody = { extractCurrentContinuation(stmt) },
        extractAfter = { registersOfCurrentContract.c0.value },
        hasBreak = hasBreak,
        parentLocation = stmt.location
    )

    private inline fun doRepeat(
        scope: TvmStepScopeManager,
        parentLocation: TvmInstLocation,
        crossinline extractBody: TvmState.() -> TvmContinuation?,
        crossinline extractAfter: TvmState.() -> TvmContinuation,
        hasBreak: Boolean,
    ) = with(ctx) {
        val body =
            scope.calcOnState { extractBody() }
                ?: return@with scope.doWithState(throwTypeCheckError)

        val wrappedBody = TvmLoopEntranceContinuation(body, loopIdx++, parentLocation)
        val count = scope.takeLastIntOrThrowTypeError() ?: return
        val inRangeConstraint = signedIntegerFitsBits(count, bits = 32u)

        scope.fork(
            inRangeConstraint,
            falseStateIsExceptional = true,
            blockOnFalseState = throwIntegerOutOfRangeError
        ) ?: return

        val after = scope.calcOnState { registerBreakpoint(extractAfter(), hasBreak) }
        val repeatCont = TvmRepeatContinuation(wrappedBody, after, count)

        scope.jumpToContinuation(repeatCont)
    }

    private fun visitUntilInst(
        scope: TvmStepScopeManager,
        stmt: TvmContLoopsInst,
        hasBreak: Boolean,
    ) = doUntil(
        scope = scope,
        extractBody = { stack.takeLastContinuation() },
        extractAfter = { extractCurrentContinuation(stmt, saveC0 = true) },
        hasBreak = hasBreak,
        parentLocation = stmt.location
    )

    private fun visitUntilEndInst(
        scope: TvmStepScopeManager,
        stmt: TvmContLoopsInst,
        hasBreak: Boolean,
    ) = doUntil(
        scope = scope,
        extractBody = { extractCurrentContinuation(stmt) },
        extractAfter = { registersOfCurrentContract.c0.value },
        hasBreak = hasBreak,
        parentLocation = stmt.location
    )

    private inline fun doUntil(
        scope: TvmStepScopeManager,
        crossinline extractBody: TvmState.() -> TvmContinuation?,
        crossinline extractAfter: TvmState.() -> TvmContinuation,
        hasBreak: Boolean,
        parentLocation: TvmInstLocation,
    ) {
        val body =
            scope.calcOnState { extractBody() }
                ?: return scope.doWithState(ctx.throwTypeCheckError)
        val wrappedBody = TvmLoopEntranceContinuation(body, loopIdx++, parentLocation)

        scope.doWithState {
            val after = scope.calcOnState { registerBreakpoint(extractAfter(), hasBreak) }
            val untilCont = TvmUntilContinuation(wrappedBody, after)
            val registers = registersOfCurrentContract
            registers.c0 = C0Register(untilCont)
        }

        scope.jumpToContinuation(wrappedBody)
    }

    private fun visitWhileInst(
        scope: TvmStepScopeManager,
        stmt: TvmContLoopsInst,
        hasBreak: Boolean,
    ) = doWhile(
        scope = scope,
        extractBody = { stack.takeLastContinuation() },
        extractAfter = { extractCurrentContinuation(stmt, saveC0 = true) },
        hasBreak = hasBreak,
        parentLocation = stmt.location
    )

    private fun visitWhileEndInst(
        scope: TvmStepScopeManager,
        stmt: TvmContLoopsInst,
        hasBreak: Boolean,
    ) = doWhile(
        scope = scope,
        extractBody = { extractCurrentContinuation(stmt) },
        extractAfter = { registersOfCurrentContract.c0.value },
        hasBreak = hasBreak,
        parentLocation = stmt.location
    )

    private inline fun doWhile(
        scope: TvmStepScopeManager,
        crossinline extractBody: TvmState.() -> TvmContinuation?,
        crossinline extractAfter: TvmState.() -> TvmContinuation,
        hasBreak: Boolean,
        parentLocation: TvmInstLocation,
    ) {
        val body =
            scope.calcOnState { extractBody() }
                ?: return scope.doWithState(ctx.throwTypeCheckError)
        val cond =
            scope.calcOnState { stack.takeLastContinuation() }
                ?: return scope.doWithState(ctx.throwTypeCheckError)
        val wrappedCond = TvmLoopEntranceContinuation(cond, loopIdx++, parentLocation)

        scope.doWithState {
            val after = scope.calcOnState { registerBreakpoint(extractAfter(), hasBreak) }
            val whileCond = TvmWhileContinuation(wrappedCond, body, after, isCondition = true)
            val registers = registersOfCurrentContract
            registers.c0 = C0Register(whileCond)
        }

        scope.jumpToContinuation(wrappedCond)
    }

    private fun visitAgainInst(
        scope: TvmStepScopeManager,
        stmt: TvmContLoopsInst,
        hasBreak: Boolean,
    ) {
        val body =
            scope.calcOnState { stack.takeLastContinuation() }
                ?: return scope.doWithState(ctx.throwTypeCheckError)
        val wrappedBody = TvmLoopEntranceContinuation(body, loopIdx++, stmt.location)

        if (hasBreak) {
            scope.doWithState {
                val registers = registersOfCurrentContract
                registers.c1 = C1Register(extractCurrentContinuation(stmt, saveC0 = true, saveC1 = true))
            }
        }

        val cont = TvmAgainContinuation(wrappedBody)

        scope.jumpToContinuation(cont)
    }

    private fun visitAgainEndInst(
        scope: TvmStepScopeManager,
        stmt: TvmContLoopsInst,
        hasBreak: Boolean,
    ) {
        val body = scope.calcOnState { extractCurrentContinuation(stmt) }
        val wrappedBody = TvmLoopEntranceContinuation(body, loopIdx++, stmt.location)

        if (hasBreak) {
            scope.doWithState {
                val registers = registersOfCurrentContract
                val newC0 = registers.c0.value.defineC1(registers.c1.value)
                registers.c0 = C0Register(newC0)
                registers.c1 = C1Register(registers.c0.value)
            }
        }

        val cont = TvmAgainContinuation(wrappedBody)

        scope.jumpToContinuation(cont)
    }
}
