package org.usvm.machine.state

import org.usvm.machine.state.messages.ReceivedMessage

typealias EventId = Long

/**
 * When smart contracts on TON network are executed, they follow the actor model ---
 * that is, each contract is being triggered by some message and at the end of execution
 * (at the compute phase) it sends zero or more messages to other contract.
 * Thus, the tree of contracts can be constructed, where a directed edge implies a sent message,
 * each node is labeled with the contract executed (multiple nodes might have the same contract) and the
 * contract execution result. In such a tree, each node has zero or one incoming edges.
 *
 * We will call an *event* a node of such a tree with an incoming edge. The corresponding class contains
 * the data from the mentioned graph objects.
 */
sealed interface TvmEventLogEntry {
    val id: EventId
}

data class TvmMessageDrivenContractExecutionEntry(
    override val id: EventId,
    val executionBegin: Long,
    val executionEnd: Long,
    val contractId: ContractId,
    val incomingMessage: ReceivedMessage,
    val methodResult: TvmMethodResult,
) : TvmEventLogEntry
