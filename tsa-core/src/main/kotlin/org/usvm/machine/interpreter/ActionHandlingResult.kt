package org.usvm.machine.interpreter

import org.usvm.machine.Int257Expr
import org.usvm.machine.state.TvmResult

interface ActionHandlingResult {
    data class Success(
        val balanceLeft: Int257Expr,
        val messagesSent: List<TvmTransactionInterpreter.MessageWithMaybeReceiver>,
    ) : ActionHandlingResult

    data class RealFailure(
        val failure: TvmResult.TvmErrorExit,
    ) : ActionHandlingResult

    data class SoftFailure(
        val failure: TvmResult.TvmSoftFailureExit,
    ) : ActionHandlingResult
}
