package org.usvm.machine.state

import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.machine.TvmContext.TvmInt257Sort

data class TvmSignatureCheck(
    val hash: UExpr<TvmInt257Sort>,
    // 512 bits
    val signature: UExpr<UBvSort>,
    val publicKey: UExpr<TvmInt257Sort>,
)
