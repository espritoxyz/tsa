package org.usvm.machine

import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import java.math.BigInteger

data class TvmConcreteGeneralData(
    val initialSenderBits: String? = null,
    val initialOpcode: UInt? = null,
) {
    init {
        checkAddressBits(initialSenderBits)
    }
}

data class TvmConcreteContractData(
    val contractC4: Cell? = null,
    val initialBalance: BigInteger? = null,
    val addressBits: String? = null,
) {
    init {
        checkAddressBits(addressBits)
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun String.hexToCell(): Cell = BagOfCells(this.hexToByteArray()).roots.single()

private fun checkAddressBits(addressBits: String?) {
    check(addressBits?.matches(addressBitsRegex) != false) {
        "Invalid address bits: $addressBits"
    }
}

private val addressBitsRegex = "10{10}[10]{256}".toRegex()
