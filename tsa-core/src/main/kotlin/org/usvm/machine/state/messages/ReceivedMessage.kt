package org.usvm.machine.state.messages

import org.usvm.machine.state.ContractId
import org.usvm.machine.state.EventId
import org.usvm.machine.state.input.ReceiverInput

data class ContractSender(
    val contractId: ContractId,
    val eventId: EventId,
)

sealed interface ReceivedMessage {
    data class InputMessage(
        val input: ReceiverInput,
    ) : ReceivedMessage

    data class MessageFromOtherContract(
        val sender: ContractSender,
        val receiver: ContractId,
        val message: MessageAsStackArguments,
    ) : ReceivedMessage
}

fun ReceivedMessage.getMsgBodySlice() =
    when (this) {
        is ReceivedMessage.InputMessage -> this.input.msgBodySliceMaybeBounced
        is ReceivedMessage.MessageFromOtherContract -> this.message.msgBodySlice
    }
