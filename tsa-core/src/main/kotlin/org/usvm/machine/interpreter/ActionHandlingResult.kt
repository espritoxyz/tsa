package org.usvm.machine.interpreter

import org.usvm.machine.Int257Expr
import org.usvm.machine.state.TvmMethodResult

interface ActionHandlingResult {
    data class Success(
        val balanceLeft: Int257Expr,
        val messagesSent: List<TvmTransactionInterpreter.MessageWithMaybeReceiver>,
    ) : ActionHandlingResult

    data class Failure(
        val failure: TvmMethodResult.TvmErrorExit,
    ) : ActionHandlingResult
}
