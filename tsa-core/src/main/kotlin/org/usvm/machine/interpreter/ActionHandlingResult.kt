package org.usvm.machine.interpreter

import org.usvm.machine.Int257Expr

interface ActionHandlingResult {
    data class Success(
        val balanceLeft: Int257Expr,
        val messagesSent: List<TvmTransactionInterpreter.MessageWithMaybeReceiver>,
    ) : ActionHandlingResult

    data class Failure(
        val exitCode: Int,
    ) : ActionHandlingResult
}
