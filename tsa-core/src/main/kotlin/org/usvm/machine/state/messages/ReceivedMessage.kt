package org.usvm.machine.state.messages

import kotlinx.serialization.Serializable
import org.usvm.UHeapRef
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.EventId
import org.usvm.machine.state.input.ReceiverInput

@Serializable
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

fun ReceivedMessage.getMsgBodySlice(): UHeapRef =
    when (this) {
        is ReceivedMessage.InputMessage -> this.input.msgBodySliceMaybeBounced
        is ReceivedMessage.MessageFromOtherContract -> this.message.messageBody.value
    }

fun ReceivedMessage.getMsgValue() =
    when (this) {
        is ReceivedMessage.InputMessage -> input.msgValue
        is ReceivedMessage.MessageFromOtherContract -> message.msgValue
    }

fun ReceivedMessage.bounce() =
    when (this) {
        is ReceivedMessage.InputMessage -> {
            input.bounce.let {
                with(it.ctx.tctx()) { it.toBv257Bool() }
            }
        }

        is ReceivedMessage.MessageFromOtherContract -> {
            message.commonInfo.flags.bounce
        }
    }

fun ReceivedMessage.bounced() =
    when (this) {
        is ReceivedMessage.InputMessage -> {
            input.bounced.let {
                with(it.ctx.tctx()) { it.toBv257Bool() }
            }
        }

        is ReceivedMessage.MessageFromOtherContract -> {
            message.commonInfo.flags.bounced
        }
    }

fun ReceivedMessage.srcAddressSlice() =
    when (this) {
        is ReceivedMessage.InputMessage -> input.srcAddressSlice
        is ReceivedMessage.MessageFromOtherContract -> message.commonInfo.srcAddressSlice
    }

fun ReceivedMessage.fwdFee() =
    when (this) {
        is ReceivedMessage.InputMessage -> input.fwdFee
        is ReceivedMessage.MessageFromOtherContract -> message.commonInfo.fwdFee
    }

fun ReceivedMessage.createdLt() =
    when (this) {
        is ReceivedMessage.InputMessage -> input.createdLt
        is ReceivedMessage.MessageFromOtherContract -> message.commonInfo.createdLt
    }

fun ReceivedMessage.createdAt() =
    when (this) {
        is ReceivedMessage.InputMessage -> input.createdAt
        is ReceivedMessage.MessageFromOtherContract -> message.commonInfo.createdAt
    }

fun ReceivedMessage.msgValue() =
    when (this) {
        is ReceivedMessage.InputMessage -> input.msgValue
        is ReceivedMessage.MessageFromOtherContract -> message.commonInfo.msgValue
    }

fun ReceivedMessage.stateInit() =
    when (this) {
        is ReceivedMessage.InputMessage -> null
        is ReceivedMessage.MessageFromOtherContract -> message.stateInitCell
    }
