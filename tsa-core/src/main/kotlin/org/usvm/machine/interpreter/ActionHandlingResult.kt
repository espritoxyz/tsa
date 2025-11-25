package org.usvm.machine.interpreter

import org.usvm.machine.Int257Expr
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmResult
import org.usvm.machine.state.messages.TlbInternalMessageContent

data class DispatchedMessage(
    val receiver: ContractId?,
    val content: TlbInternalMessageContent,
)

interface ActionHandlingResult {
    data class Success(
        val balanceLeft: Int257Expr,
        val messagesSent: List<DispatchedMessage>,
    ) : ActionHandlingResult

    data class RealFailure(
        val failure: TvmResult.TvmErrorExit,
    ) : ActionHandlingResult

    data class SoftFailure(
        val failure: TvmResult.TvmSoftFailureExit,
    ) : ActionHandlingResult
}
