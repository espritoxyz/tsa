package org.usvm.machine.state.messages

import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TvmContext

data class OutMessage(
    val msgValue: UExpr<TvmContext.TvmInt257Sort>,
    val fullMsgCell: UHeapRef,
    val msgBodySlice: UHeapRef,
    val destAddrSlice: UHeapRef,
)
