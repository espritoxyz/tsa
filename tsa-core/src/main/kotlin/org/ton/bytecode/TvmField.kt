package org.ton.bytecode

import org.usvm.machine.types.TvmType

interface TvmField {
    val name: String
}

data class TvmFieldImpl(
    val enclosingType: TvmType,
    override val name: String,
) : TvmField
