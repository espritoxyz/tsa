package org.usvm.machine

import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import java.math.BigInteger

data class TvmConcreteGeneralData(
    val initialInputConcreteData: MessageConcreteData = MessageConcreteData(),
    // map key - additional input id
    val inputData: Map<Int, MessageConcreteData> = emptyMap(),
)

data class MessageConcreteData(
    val opcodeInfo: OpcodeInfo? = null,
    val senderBits: String? = null,
) {
    init {
        checkAddressBits(senderBits)
    }
}

sealed interface OpcodeInfo

data class ConcreteOpcode(
    val opcode: BigInteger,
) : OpcodeInfo

data class ExcludedOpcodes(
    val opcodes: Set<BigInteger>,
) : OpcodeInfo

// msg body is shorter than opcode length (32 bits)
data object NoOpcode : OpcodeInfo

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
