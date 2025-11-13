package org.usvm.test.resolver

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.EventId
import org.usvm.machine.state.messages.ContractSender

@Serializable
sealed interface TvmTestInput {
    val usedParameters: List<TvmTestValue>

    @Serializable
    @SerialName("stackInput")
    data class StackInput(
        override val usedParameters: List<TvmTestValue>,
    ) : TvmTestInput

    @Serializable
    @SerialName("recvInternalInput")
    data class RecvInternalInput(
        val srcAddress: TvmTestSliceValue,
        val msgBody: TvmTestSliceValue,
        val msgValue: TvmTestIntegerValue,
        val bounce: Boolean,
        val bounced: Boolean,
        val ihrDisabled: Boolean,
        val ihrFee: TvmTestIntegerValue,
        val fwdFee: TvmTestIntegerValue,
        val createdLt: TvmTestIntegerValue,
        val createdAt: TvmTestIntegerValue,
    ) : TvmTestInput {
        override val usedParameters: List<TvmTestValue>
            get() =
                listOf(
                    srcAddress,
                    msgBody,
                    msgValue,
                    TvmTestBooleanValue(bounce),
                    TvmTestBooleanValue(bounced),
                    TvmTestBooleanValue(ihrDisabled),
                    ihrFee,
                    fwdFee,
                    createdLt,
                    createdAt,
                )
    }

    @Serializable
    @SerialName("recvExternalInput")
    data class RecvExternalInput(
        val msgBody: TvmTestSliceValue,
        val wasAccepted: Boolean,
    ) : TvmTestInput {
        override val usedParameters: List<TvmTestValue>
            get() = listOf(msgBody)
    }

    sealed interface ReceivedTestMessage {
        data class InputMessage(
            val input: TvmTestInput,
        ) : ReceivedTestMessage

        data class MessageFromOtherContract(
            val sender: ContractSender,
            val receiver: ContractId,
            val message: TvmTestMessage,
        ) : ReceivedTestMessage
    }
}

data class TvmMessageDrivenContractExecutionTestEntry(
    val id: EventId,
    val executionBegin: Int,
    val executionEnd: Int,
    val contractId: ContractId,
    val incomingMessage: TvmTestInput.ReceivedTestMessage,
    val methodResult: TvmMethodSymbolicResult,
    val gasUsageHistory: Int,
    val computeFee: TvmTestIntegerValue?,
)
