package org.usvm.machine.state.messages

import org.usvm.UHeapRef
import org.usvm.machine.Int257Expr

object MessageMode {
    const val SEND_REMAINING_BALANCE_BIT = 7

    const val SEND_REMAINING_VALUE_BIT = 6

    const val SEND_FEES_SEPARATELY = 0
}

data class MessageActionParseResult(
    val content: MessageAsStackArguments,
    val sendMessageMode: Int257Expr,
)

/**
 * represents the arguments that are used in `recv_internal_message` / `recv_external_message`
 * @param destAddrSlice is  an exception to the rule above and contains a destination address that
 * was extracted from the sent message; in tsa, the receiver is resolved via contract id match in
 * communication scheme and not by an actual address, which means, that [destAddrSlice] is not
 * necessarily equal to the corresponding c7 parameter of the destination contract.
 */
data class MessageAsStackArguments(
    val msgValue: Int257Expr,
    val fullMsgCell: UHeapRef,
    val msgBodySlice: UHeapRef,
    val destAddrSlice: UHeapRef,
)
