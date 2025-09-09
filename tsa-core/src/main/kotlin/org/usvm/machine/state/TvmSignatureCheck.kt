package org.usvm.machine.state

import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.machine.TvmContext.TvmInt257Sort

data class TvmSignatureCheck(
    val hash: UExpr<TvmInt257Sort>,
    val signature: UExpr<UBvSort>, // 512 bits
    val publicKey: UExpr<TvmInt257Sort>,
    val checkPassed: Boolean
)
