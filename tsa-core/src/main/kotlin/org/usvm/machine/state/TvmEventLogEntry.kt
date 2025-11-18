package org.usvm.machine.state

import org.usvm.UExpr
import org.usvm.machine.TvmContext
import org.usvm.machine.state.messages.ReceivedMessage

/**
 * EventId is equal to the number of already executed instruction in state.
 * It is unlikely that this number will exceed the boundaries of the Int type
 * (at least for the reason we are storing all the gas usages in a collection
 * that must have a size within Int boundaries).
 */
typealias EventId = Int

/**
 * When smart contracts on TON network are executed, they follow the actor model ---
 * that is, each contract is being triggered by some message and at the end of execution
 * (at the action phase) it sends zero or more messages to other contract.
 * Thus, the tree of contracts can be constructed, where a directed edge implies a sent message,
 * each node is labeled with the contract executed (multiple nodes might have the same contract) and the
 * contract execution result (i.e. an exit code). In such a tree, each node has zero or one incoming edges.
 *
 * We will call an *event* a node of such a tree with an incoming edge. The corresponding class contains
 * the data from the mentioned graph objects.
 */
sealed interface TvmEventLogEntry {
    val id: EventId
}

/**
 * This class is only reasonable within the context of the state where the entry occurred.
 * For example, the gas usage history would be evaluated as
 * `state.gasUsageHistory(executionBegin, executionEnd)`.
 * @property executionBegin is the beginning time of the event, where the time is measured as the
 * number of already executed instructions within this state.
 * @see TvmState.pseudologicalTime
 */
data class TvmMessageDrivenContractExecutionEntry(
    override val id: EventId,
    val executionBegin: Int,
    val executionEnd: Int,
    val contractId: ContractId,
    val incomingMessage: ReceivedMessage,
    val computePhaseResult: TvmResult,
    var actionPhaseResult: TvmResult?, // might be set after creation
    val computeFee: UExpr<TvmContext.TvmInt257Sort>,
) : TvmEventLogEntry
