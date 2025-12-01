package org.usvm.machine.interpreter

import org.usvm.machine.Int257Expr
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmResult
import org.usvm.machine.state.messages.TlbInternalMessageContent

/**
 * Represents a message that is already sent into the blockchain, but not necessarily yet received by some contract
 */
data class DispatchedMessage(
    val receiver: ContractId?,
    val content: TlbInternalMessageContent,
)

interface ActionHandlingResult {
    /**
     * @param messagesDispatched contains the messages dispatched by the executing contract in the same order
     * they were in the action list of the contract.
     */
    data class Success(
        val balanceLeft: Int257Expr,
        val messagesDispatched: List<DispatchedMessage>,
    ) : ActionHandlingResult

    data class RealFailure(
        val failure: TvmResult.TvmErrorExit,
    ) : ActionHandlingResult

    data class SoftFailure(
        val failure: TvmResult.TvmSoftFailureExit,
    ) : ActionHandlingResult
}
