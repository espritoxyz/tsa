package org.usvm.machine.interpreter

import org.usvm.machine.Int257Expr
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmResult
import org.usvm.machine.state.messages.ConstructedMessageCells
import org.usvm.machine.state.messages.MessageAsStackArguments
import org.usvm.machine.state.messages.MessageSource
import org.usvm.machine.state.messages.TlbInternalMessageContent

data class DispatchedMessageContent(
    val tlbContent: TlbInternalMessageContent,
    val messageCells: ConstructedMessageCells,
) {
    fun toStackArgs(): MessageAsStackArguments {
        val constructedCells = messageCells
        return MessageAsStackArguments(
            tlbContent.commonMessageInfo.msgValue,
            constructedCells.fullMsgCell,
            constructedCells.msgBodySlice,
            tlbContent.commonMessageInfo.dstAddressSlice,
            source = MessageSource.Bounced,
        )
    }
}

data class DispatchedUnconstructedMessage(
    val receiver: ContractId?,
    val content: TlbInternalMessageContent,
)

/**
 * Represents a message that is already sent into the blockchain, but not necessarily yet received by some contract
 */
data class DispatchedMessage(
    val receiver: ContractId?,
    val content: DispatchedMessageContent,
)

interface ActionHandlingResult {
    /**
     * @param messagesDispatched contains the messages dispatched by the executing contract in the same order
     * they were in the action list of the contract.
     */
    data class Success(
        val balanceLeft: Int257Expr,
        val messagesDispatched: List<DispatchedUnconstructedMessage>,
    ) : ActionHandlingResult

    data class RealFailure(
        val failure: TvmResult.TvmErrorExit,
    ) : ActionHandlingResult

    data class SoftFailure(
        val failure: TvmResult.TvmSoftFailureExit,
    ) : ActionHandlingResult
}
