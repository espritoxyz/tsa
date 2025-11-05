package org.usvm.machine.state.messages

import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TvmContext

data class FwdFeeInfo(
    val symbolicFwdFee: UExpr<TvmContext.TvmInt257Sort>,
    val stateInitRef: UHeapRef?, // null if inlined
    val msgBodyRef: UHeapRef?, // null if inlined
)
