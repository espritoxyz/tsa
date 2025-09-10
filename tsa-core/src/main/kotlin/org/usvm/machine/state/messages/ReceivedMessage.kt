package org.usvm.machine.state.messages

import org.usvm.machine.state.ContractId
import org.usvm.machine.state.input.ReceiverInput

sealed interface ReceivedMessage {
    data class InputMessage(
        val input: ReceiverInput,
    ) : ReceivedMessage

    data class MessageFromOtherContract(
        val sender: ContractId,
        val receiver: ContractId,
        val message: OutMessage,
    ) : ReceivedMessage
}

fun ReceivedMessage.getMsgBodySlice() =
    when (this) {
        is ReceivedMessage.InputMessage -> this.input.msgBodySliceMaybeBounced
        is ReceivedMessage.MessageFromOtherContract -> this.message.msgBodySlice
    }
