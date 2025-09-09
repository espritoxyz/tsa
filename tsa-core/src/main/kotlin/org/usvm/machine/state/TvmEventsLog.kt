package org.usvm.machine.state

import org.usvm.machine.state.messages.ReceivedMessage

typealias EventId = Long


sealed interface TvmEventLogEntry {
    val id: EventId
}

data class TvmMessageDrivenContractExecutionEntry(
    override val id: EventId,
    val executionBegin: Long,
    val executionEnd: Long,
    val contractId: ContractId,
    val incomingMessage: ReceivedMessage,
    val incomingMessageSender: Long,
    val methodResult: TvmMethodResult,
) : TvmEventLogEntry


data class TvmRootCheckerContractExecutionEntry(
    override val id: EventId,
    val contractId: ContractId,
) : TvmEventLogEntry