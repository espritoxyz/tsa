package org.usvm.machine.interpreter

import org.ton.bytecode.TvmContStackInst
import org.ton.bytecode.TvmContStackSetcontvarargsInst
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.intValue
import org.usvm.machine.state.TvmStack
import org.usvm.machine.state.addContinuation
import org.usvm.machine.state.checkOutOfRange
import org.usvm.machine.state.consumeGas
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.takeLastContinuation
import org.usvm.machine.state.takeLastIntOrThrowTypeError

class TvmContinuationStackInterpreter(
    private val ctx: TvmContext
) {
    fun visitContStackInst(
        scope: TvmStepScopeManager,
        stmt: TvmContStackInst
    ) {
        when (stmt) {
            is TvmContStackSetcontvarargsInst -> visitSetContVarargs(scope, stmt)
            else -> TODO("$stmt")
        }
    }

    private fun visitSetContVarargs(
        scope: TvmStepScopeManager,
        stmt: TvmContStackSetcontvarargsInst
    ) {
        // TODO consume correct amount of gas
        scope.doWithState { consumeGas(26) }

        val more =
            scope.takeLastIntOrThrowTypeError()
                ?: return
        val copy =
            scope.takeLastIntOrThrowTypeError()
                ?: return
        val cont =
            scope.calcOnState { stack.takeLastContinuation() }
                ?: return scope.doWithState(ctx.throwTypeCheckError)

        checkOutOfRange(more, scope, min = -1, max = 255)
            ?: return
        checkOutOfRange(copy, scope, min = 0, max = 255)
            ?: return

        val curStack = scope.calcOnState { stack }
        val moreVal = more.intValue()
        val copyVal = copy.intValue()
        var contStack = cont.stack?.clone()
        var contNargs = cont.nargs

        if (copyVal > 0) {
            if (contNargs != null && contNargs < copyVal.toUInt()) {
                return scope.doWithState(ctx.throwStackOverflowError)
            }

            if (contStack == null) {
                contStack = TvmStack(ctx, allowInputValues = false)
            }
            contStack.takeValuesFromOtherStack(curStack, copyVal)

            if (contNargs != null) {
                contNargs -= copyVal.toUInt()
            }
        }

        if (moreVal >= 0) {
            if (contNargs != null && contNargs > moreVal.toUInt()) {
                // TODO interpreter has the following code
                // cdata->nargs = 0x40000000;  // will throw an exception if run
                return scope.doWithState(ctx.throwStackOverflowError)
            } else if (contNargs == null) {
                contNargs = moreVal.toUInt()
            }
        }

        scope.doWithState {
            stack.addContinuation(cont.update(newStack = contStack, newNargs = contNargs))
            newStmt(stmt.nextStmt())
        }
    }
}
