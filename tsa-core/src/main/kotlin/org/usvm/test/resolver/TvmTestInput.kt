package org.usvm.test.resolver

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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

    sealed interface ReceiverInput : TvmTestInput {
        val msgBody: TvmTestSliceValue
    }

    @Serializable
    @SerialName("recvInternalInput")
    data class RecvInternalInput(
        val srcAddress: TvmTestSliceValue,
        override val msgBody: TvmTestSliceValue,
        val msgValue: TvmTestIntegerValue,
        val bounce: Boolean,
        val bounced: Boolean,
        val ihrDisabled: Boolean,
        val ihrFee: TvmTestIntegerValue,
        val fwdFee: TvmTestIntegerValue,
        val createdLt: TvmTestIntegerValue,
        val createdAt: TvmTestIntegerValue,
    ) : ReceiverInput {
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
        override val msgBody: TvmTestSliceValue,
        val wasAccepted: Boolean,
    ) : ReceiverInput {
        override val usedParameters: List<TvmTestValue>
            get() = listOf(msgBody)
    }

    @Serializable
    sealed interface ReceivedTestMessage {
        @Serializable
        data class InputMessage(
            val input: TvmTestInput,
        ) : ReceivedTestMessage

        @Serializable
        data class MessageFromOtherContract(
            val sender: ContractSender,
            val receiver: ContractId,
            val message: TvmTestMessage,
        ) : ReceivedTestMessage
    }
}

@Serializable
data class TvmMessageDrivenContractExecutionTestEntry(
    val id: EventId,
    val executionBegin: Int,
    val executionEnd: Int,
    val contractId: ContractId,
    val incomingMessage: TvmTestInput.ReceivedTestMessage,
    @Transient val computePhaseResult: TvmTestResult? = null,
    @Transient val actionPhaseResult: TvmTestResult? = null,
    val gasUsage: Int,
    val computeFee: TvmTestIntegerValue,
    val eventTime: TvmTestIntegerValue,
    val computePhaseResultSummary: String = computePhaseResult.toString(),
    val actionPhaseResultSummary: String = actionPhaseResult.toString(),
)
