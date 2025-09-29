package org.usvm.machine.state.messages

import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.Int257Expr
import org.usvm.machine.TvmContext

object MessageMode {
    const val SEND_REMAINING_BALANCE = 128
    const val SEND_REMAINING_VALUE = 64
}

data class OutMessage(
    val msgValue: UExpr<TvmContext.TvmInt257Sort>,
    val fullMsgCell: UHeapRef,
    val msgBodySlice: UHeapRef,
    val destAddrSlice: UHeapRef,
    val sendMessageMode: Int257Expr,
)
