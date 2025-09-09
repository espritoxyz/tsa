package org.usvm.machine.state

import org.ton.bytecode.TsaArtificialCheckerReturn
import org.ton.bytecode.TsaArtificialJmpToContInst
import org.ton.bytecode.TvmAgainContinuation
import org.ton.bytecode.TvmCellValue
import org.ton.bytecode.TvmContinuation
import org.ton.bytecode.TvmExceptionContinuation
import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmInstLambdaLocation
import org.ton.bytecode.TvmLambda
import org.ton.bytecode.TvmLoopEntranceContinuation
import org.ton.bytecode.TvmMethod
import org.ton.bytecode.TvmMethodReturnContinuation
import org.ton.bytecode.TvmOrdContinuation
import org.ton.bytecode.TvmQuitContinuation
import org.ton.bytecode.TvmRegisterSavelist
import org.ton.bytecode.TvmRepeatContinuation
import org.ton.bytecode.TvmUntilContinuation
import org.ton.bytecode.TvmWhileContinuation
import org.usvm.UHeapRef
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.extractMethodIdOrNull
import org.usvm.machine.interpreter.TsaCheckerFunctionsInterpreter
import org.usvm.machine.state.TvmStack.TvmStackTupleValue
import org.usvm.machine.state.TvmStack.TvmStackTupleValueConcreteNew
import org.usvm.utils.intValueOrNull

fun TvmState.extractCurrentContinuation(
    stmt: TvmInst,
    saveC0: Boolean = false,
    saveC1: Boolean = false,
    saveC2: Boolean = false
): TvmOrdContinuation =
    with(ctx) {
        var c0: C0Register? = null
        var c1: C1Register? = null
        var c2: C2Register? = null

        val registers = registersOfCurrentContract

        if (saveC0) {
            c0 = registers.c0
            registers.c0 = C0Register(quit0Cont)
        }

        if (saveC1) {
            c1 = registers.c1
            registers.c1 = C1Register(quit1Cont)
        }

        if (saveC2) {
            c2 = registers.c2

            // this line is commented in the tvm interpreter
            // https://github.com/ton-blockchain/ton/blob/5c392e0f2d946877bb79a09ed35068f7b0bd333a/crypto/vm/vm.cpp#L382
            // registers.c2 = C2Register(TvmExceptionContinuation)
        }

        TvmOrdContinuation(stmt.nextStmt(), sourceCell = null, TvmRegisterSavelist(c0, c1, c2))
    }

fun TvmStepScopeManager.callMethod(
    stmt: TvmInst,
    methodToCall: TvmMethod,
    checkerMemorySavelist: TsaCheckerFunctionsInterpreter.CheckerMemorySavelist? = null
) {
    val nextContinuation = TvmOrdContinuation(methodToCall, sourceCell = null)

    doWithState {
        val retCont =
            TvmOrdContinuation(
                stmt = stmt.nextStmt(),
                savelist = TvmRegisterSavelist(registersOfCurrentContract.c0),
                sourceCell = null
            )
        val wrappedRet =
            if (checkerMemorySavelist != null) {
                val location = TvmInstLambdaLocation(0)
                location.parent = stmt.location
                val lambda = TvmLambda(mutableListOf(TsaArtificialCheckerReturn(location, checkerMemorySavelist)))
                val wrappedCont =
                    TvmOrdContinuation(
                        stmt = lambda.instList.first(),
                        savelist = TvmRegisterSavelist(C0Register(retCont)),
                        sourceCell = null
                    )
                TvmMethodReturnContinuation(methodToCall.id, wrappedCont)
            } else {
                TvmMethodReturnContinuation(methodToCall.id, retCont)
            }
        registersOfCurrentContract.c0 = C0Register(wrappedRet)

        callStack.push(methodToCall, returnSite = stmt)
    }

    jump(nextContinuation)
}

fun TvmStepScopeManager.jumpToContinuation(cont: TvmContinuation) {
    if (cont.stack != null || cont.nargs != null) {
        jumpToContinuationComplex(cont, passArgs = null)
    } else {
        jump(cont)
    }
}

fun TvmStepScopeManager.jumpToContinuationComplex(
    cont: TvmContinuation,
    passArgs: UInt? = null
) = doWithStateCtx {
    // TODO stack depth checks ?

    var copy = cont.nargs
    if (passArgs != null && copy == null) {
        copy = passArgs
    }

    // copy == null : pass whole stack, else pass top `copy` elements, drop the remainder.
    val contStack = cont.stack
    if (contStack != null) {
        if (copy == null) {
            // To ensure correctness stack must have concrete depth
            copy = stack.depth()
        }

        val newStack = contStack.clone()
        newStack.takeValuesFromOtherStack(stack, copy.toInt())
        stack = newStack
    } else {
        // if copy == null, the whole stack is passed
        if (copy != null) {
            val newStack = TvmStack(ctx, allowInputValues = false)
            newStack.takeValuesFromOtherStack(stack, copy.toInt())
            stack = newStack
        }
    }

    jump(cont)
}

fun TvmStepScopeManager.callContinuation(
    stmt: TvmInst,
    continuation: TvmContinuation
) = doWithState {
    if (continuation.savelist.c0 != null) {
        // call reduces to a jump
        return@doWithState jumpToContinuation(continuation)
    }

    if (continuation.stack != null || continuation.nargs != null) {
        return@doWithState callContinuationComplex(stmt, continuation, null, null)
    }

    val retCont =
        TvmOrdContinuation(
            stmt = stmt.nextStmt(),
            savelist = TvmRegisterSavelist(registersOfCurrentContract.c0),
            sourceCell = null
        )
    registersOfCurrentContract.c0 = C0Register(retCont)
    jump(continuation)
}

fun TvmStepScopeManager.callContinuationComplex(
    stmt: TvmInst,
    continuation: TvmContinuation,
    passArgs: UInt? = null,
    returnArgs: UInt? = null
) = doWithStateCtx {
    if (continuation.savelist.c0 != null) {
        // call reduces to a jump
        return@doWithStateCtx jumpToContinuationComplex(continuation, passArgs)
    }

    val contNargs = continuation.nargs
    if (contNargs != null && passArgs != null && contNargs > passArgs) {
        // stack underflow while calling a closure continuation: not enough arguments passed
        return@doWithStateCtx throwStackUnderflowError(this)
    }

    var copy = continuation.nargs
    var skip = 0u

    if (passArgs != null) {
        if (copy != null) {
            skip = passArgs - copy
        } else {
            copy = passArgs
        }
    }

    // copy == null : pass whole stack, else pass top `copy` elements, drop next `skip` elements.
    val contStack = continuation.stack
    val (newStack, remainder) =
        if (contStack != null) {
            if (copy == null) {
                // To ensure correctness stack must have concrete depth
                copy = stack.depth()
            }

            val newStack = contStack.clone()
            newStack.takeValuesFromOtherStack(stack, copy.toInt())

            if (skip > 0u) {
                stack.dropLastEntries(skip)
            }

            newStack to stack
        } else if (copy != null) {
            val newStack = TvmStack(ctx, allowInputValues = false)
            newStack.takeValuesFromOtherStack(stack, copy.toInt())
            if (skip > 0u) {
                stack.dropLastEntries(skip)
            }

            newStack to stack
        } else {
            stack to null
        }

    stack = newStack

    val retCont =
        TvmOrdContinuation(
            stmt = stmt.nextStmt(),
            savelist = TvmRegisterSavelist(registersOfCurrentContract.c0),
            stack = remainder,
            nargs = returnArgs,
            sourceCell = null
        )
    registersOfCurrentContract.c0 = C0Register(retCont)
    jump(continuation)
}

fun TvmStepScopeManager.returnFromContinuation() {
    val c0 = calcOnState { registersOfCurrentContract.c0.value }

    doWithStateCtx {
        registersOfCurrentContract.c0 = C0Register(quit0Cont)
    }

    jumpToContinuation(c0)
}

fun TvmStepScopeManager.returnAltFromContinuation() {
    val c1 = calcOnState { registersOfCurrentContract.c1.value }

    doWithStateCtx {
        registersOfCurrentContract.c1 = C1Register(quit1Cont)
    }

    jumpToContinuation(c1)
}

fun TvmContinuation.defineC0(cont: TvmContinuation): TvmContinuation {
    if (savelist.c0 != null) {
        return this
    }

    return update(newSavelist = savelist.copy(c0 = C0Register(cont)))
}

fun TvmContinuation.defineC1(cont: TvmContinuation): TvmContinuation {
    if (savelist.c1 != null) {
        return this
    }

    return update(newSavelist = savelist.copy(c1 = C1Register(cont)))
}

fun TvmContinuation.defineC2(cont: TvmContinuation): TvmContinuation {
    if (savelist.c2 != null) {
        return this
    }

    return update(newSavelist = savelist.copy(c2 = C2Register(cont)))
}

fun TvmContinuation.defineC3(c3: C3Register): TvmContinuation {
    if (savelist.c3 != null) {
        return this
    }

    return update(newSavelist = savelist.copy(c3 = c3))
}

fun TvmContinuation.defineC4(cell: UHeapRef): TvmContinuation {
    if (savelist.c4 != null) {
        return this
    }

    return update(newSavelist = savelist.copy(c4 = C4Register(TvmCellValue(cell))))
}

fun TvmContinuation.defineC5(cell: UHeapRef): TvmContinuation {
    if (savelist.c5 != null) {
        return this
    }

    return update(newSavelist = savelist.copy(c5 = C5Register(TvmCellValue(cell))))
}

fun TvmContinuation.defineC7(tuple: TvmStackTupleValue): TvmContinuation {
    if (savelist.c7 != null) {
        return this
    }

    require(tuple is TvmStackTupleValueConcreteNew) {
        TODO("Support non-concrete tuples")
    }

    return update(newSavelist = savelist.copy(c7 = C7Register(tuple)))
}

/**
 * Low-level primitive for internal use. Should probably be used [jumpToContinuation] instead.
 * @see jumpToContinuation
 */
private fun TvmStepScopeManager.jump(cont: TvmContinuation) {
    when (cont) {
        is TvmOrdContinuation -> doOrdJump(cont)
        is TvmQuitContinuation -> doQuitJump(cont)
        is TvmLoopEntranceContinuation -> doLoopEntranceJump(cont)
        is TvmUntilContinuation -> doUntilJump(cont)
        is TvmRepeatContinuation -> doRepeatJump(cont)
        is TvmWhileContinuation -> doWhileJump(cont)
        is TvmAgainContinuation -> doAgainJump(cont)
        is TvmExceptionContinuation -> doExceptionJump(cont)
        is TvmMethodReturnContinuation -> doMethodReturnJump(cont)
    }
}

private fun TvmState.adjustRegisters(cont: TvmContinuation) =
    with(cont.savelist) {
        val registers = registersOfCurrentContract
        c0?.let { registers.c0 = it }
        c1?.let { registers.c1 = it }
        c2?.let { registers.c2 = it }
        c3?.let { registers.c3 = it }
        c4?.let { registers.c4 = it }
        c5?.let { registers.c5 = it }
        c7?.let { registers.c7 = it.copy() }
    }

private fun TvmStepScopeManager.doOrdJump(cont: TvmOrdContinuation) =
    doWithState {
        adjustRegisters(cont)

        newStmt(cont.stmt)
    }

private fun TvmStepScopeManager.doMethodReturnJump(cont: TvmMethodReturnContinuation) {
    doWithState {
        if (callStack.lastMethod().extractMethodIdOrNull() == cont.method) {
            callStack.pop()
        } else {
            // TODO
            // sometimes the invariant can be violated, for example,
            // by manually setting the c0 register
        }
    }

    doOrdJump(cont.returnSite)
}

private fun TvmStepScopeManager.doQuitJump(cont: TvmQuitContinuation) =
    doWithState {
        val exit =
            when (cont.exitCode) {
                0u -> TvmNormalExit
                1u -> TvmAlternativeExit
                else -> error("Unexpected exit code ${cont.exitCode}")
            }

        val registers = registersOfCurrentContract
        val commitedState = TvmCommitedState(registers.c4, registers.c5)

        lastCommitedStateOfContracts = lastCommitedStateOfContracts.put(currentContract, commitedState)

        setExit(TvmMethodResult.TvmSuccess(exit, stack))
    }

private fun TvmStepScopeManager.doLoopEntranceJump(cont: TvmLoopEntranceContinuation) {
    doWithState {
        newStmt(cont.codeBlock.instList.first())
    }
}

private fun TvmStepScopeManager.doUntilJump(cont: TvmUntilContinuation) {
    doWithState { adjustRegisters(cont) }

    val x = takeLastIntOrThrowTypeError() ?: return
    val continueLoopCondition = calcOnStateCtx { mkEq(x, ctx.zeroValue) }

    fork(
        continueLoopCondition,
        falseStateIsExceptional = false,
        blockOnFalseState = {
            newStmt(TsaArtificialJmpToContInst(cont.after, lastStmt.location))
        }
    ) ?: return

    doWithState {
        registersOfCurrentContract.c0 = C0Register(cont.update(newSavelist = TvmRegisterSavelist.EMPTY))
    }

    jump(cont.body)
}

private fun TvmStepScopeManager.doRepeatJump(cont: TvmRepeatContinuation) {
    doWithState { adjustRegisters(cont) }

    val count = cont.count

    val isPositive = calcOnStateCtx { mkBvSignedLessExpr(zeroValue, count) }

    fork(
        isPositive,
        falseStateIsExceptional = false,
        blockOnFalseState = {
            newStmt(TsaArtificialJmpToContInst(cont.after, lastStmt.location))
        }
    ) ?: return

    doWithStateCtx {
        val newCont = cont.copy(count = mkBvSubExpr(cont.count, oneValue), savelist = TvmRegisterSavelist.EMPTY)
        registersOfCurrentContract.c0 = C0Register(newCont)
    }

    jump(cont.body)
}

private fun TvmStepScopeManager.doWhileJump(cont: TvmWhileContinuation) {
    doWithState { adjustRegisters(cont) }

    val newCont = cont.copy(isCondition = !cont.isCondition, savelist = TvmRegisterSavelist.EMPTY)
    doWithState { registersOfCurrentContract.c0 = C0Register(newCont) }

    if (!cont.isCondition) {
        return jump(cont.condition)
    }

    val cond = takeLastIntOrThrowTypeError() ?: return
    val continueLoopCondition = calcOnStateCtx { mkEq(cond, zeroValue).not() }

    fork(
        continueLoopCondition,
        falseStateIsExceptional = false,
        blockOnFalseState = {
            newStmt(TsaArtificialJmpToContInst(cont.after, lastStmt.location))
        }
    ) ?: return

    jump(cont.body)
}

private fun TvmStepScopeManager.doAgainJump(cont: TvmAgainContinuation) {
    doWithState {
        adjustRegisters(cont)
        registersOfCurrentContract.c0 = C0Register(cont.update(newSavelist = TvmRegisterSavelist.EMPTY))
    }

    jump(cont.body)
}

private fun TvmStepScopeManager.doExceptionJump(cont: TvmExceptionContinuation) {
    val exitCode = takeLastIntOrThrowTypeError() ?: return
    val exitCodeValue = exitCode.intValueOrNull ?: error("Cannot extract concrete code exception")
    val failure = TvmUserDefinedFailure(exitCodeValue)

    doWithState {
        stack.addInt(exitCode)

        setExit(TvmMethodResult.TvmFailure(failure, TvmFailureType.UnknownError, phase))
    }
}
