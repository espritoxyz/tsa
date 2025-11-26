package org.usvm.machine.state.messages

import org.usvm.UExpr
import org.usvm.machine.TvmContext

data class Flags(
    val intMsgInfo: UExpr<TvmContext.TvmInt257Sort>,
    val ihrDisabled: UExpr<TvmContext.TvmInt257Sort>,
    val bounce: UExpr<TvmContext.TvmInt257Sort>,
    val bounced: UExpr<TvmContext.TvmInt257Sort>,
) {
    fun asFlagsList() = listOf(intMsgInfo, ihrDisabled, bounce, bounced)
}
