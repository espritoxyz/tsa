package org.usvm.machine.state

import org.ton.bitstring.BitString
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.CellSlice
import org.ton.hashmap.HashMapE
import org.ton.tlb.TlbCodec
import org.usvm.machine.toTvmCell
import org.ton.bytecode.TvmCell as InternalTvmCell

object TvmConfigBoc {
    private val configCell: Cell by lazy {
        val bocBytes =
            javaClass.getResourceAsStream("/config-mainnet-2026-04-16.boc")
                ?: error("config-mainnet-2026-04-16.boc resource not found")
        BagOfCells(bocBytes.readBytes()).roots.single()
    }

    private val configHashMap: HashMapE<Cell> by lazy {
        val codec = HashMapE.tlbCodec(CONFIG_KEY_LENGTH, CellIdentityCodec)
        val dictCell = Cell(BitString(true), configCell)
        codec.loadTlb(dictCell.beginParse())
    }

    val entries: Map<Int, InternalTvmCell> by lazy {
        configHashMap.associate { (key, leafCell) ->
            key.toSignedInt() to leafCell.refs.single().toTvmCell()
        }
    }

    private fun getConfigParamCell(index: Int): Cell {
        val key = BitString(index.toBitList(CONFIG_KEY_LENGTH))
        return configHashMap
            .first { it.first == key }
            .second.refs
            .first()
    }

    // The current StoragePrices record is the value of the latest entry of the param 18 dict.
    private val currentStoragePricesCell: Cell by lazy {
        val param18 = getConfigParamCell(18)
        val subCodec = HashMapE.tlbCodec(32, CellIdentityCodec)
        val subDict = Cell(BitString(true), param18)
        val subEntries = subCodec.loadTlb(subDict.beginParse())
        subEntries.last().second
    }

    val storagePrices: StoragePrices by lazy {
        parseStoragePrices(currentStoragePricesCell)
    }

    // The current StoragePrices record cell, used as the first element of the unpacked config tuple (c7[14]).
    val storagePricesEntryCell: InternalTvmCell by lazy {
        currentStoragePricesCell.toTvmCell()
    }

    val masterchainGasPrices: GasPrices by lazy {
        parseGasPrices(getConfigParamCell(20))
    }

    val basechainGasPrices: GasPrices by lazy {
        parseGasPrices(getConfigParamCell(21))
    }

    val masterchainMsgPrices: MsgPrices by lazy {
        parseMsgPrices(getConfigParamCell(24))
    }

    val basechainMsgPrices: MsgPrices by lazy {
        parseMsgPrices(getConfigParamCell(25))
    }

    private fun parseStoragePrices(cell: Cell): StoragePrices {
        val slice = cell.beginParse()
        slice.skipBits(8)
        slice.skipBits(32)
        val bitPricePs = slice.loadTinyInt(64)
        val cellPricePs = slice.loadTinyInt(64)
        val mcBitPricePs = slice.loadTinyInt(64)
        val mcCellPricePs = slice.loadTinyInt(64)
        return StoragePrices(bitPricePs, cellPricePs, mcBitPricePs, mcCellPricePs)
    }

    private fun parseGasPrices(cell: Cell): GasPrices {
        val slice = cell.beginParse()
        slice.skipBits(8)
        val flatGasLimit = slice.loadTinyInt(64)
        val flatGasPrice = slice.loadTinyInt(64)
        slice.skipBits(8)
        val gasPrice = slice.loadTinyInt(64)
        return GasPrices(flatGasLimit, flatGasPrice, gasPrice)
    }

    private fun parseMsgPrices(cell: Cell): MsgPrices {
        val slice = cell.beginParse()
        slice.skipBits(8)
        val lumpPrice = slice.loadTinyInt(64)
        val bitPrice = slice.loadTinyInt(64)
        val cellPrice = slice.loadTinyInt(64)
        slice.skipBits(32)
        val firstFrac = slice.loadTinyInt(16)
        return MsgPrices(lumpPrice, bitPrice, cellPrice, firstFrac)
    }

    private fun BitString.toSignedInt(): Int {
        var result = 0
        for (i in 0 until size) {
            result = (result shl 1) or if (get(i)) 1 else 0
        }
        return result
    }

    private fun Int.toBitList(bits: Int): List<Boolean> = (bits - 1 downTo 0).map { (this shr it) and 1 == 1 }

    data class StoragePrices(
        val bitPricePs: Long,
        val cellPricePs: Long,
        val mcBitPricePs: Long,
        val mcCellPricePs: Long,
    )

    data class GasPrices(
        val flatGasLimit: Long,
        val flatGasPrice: Long,
        val gasPrice: Long,
    )

    data class MsgPrices(
        val lumpPrice: Long,
        val bitPrice: Long,
        val cellPrice: Long,
        val firstFrac: Long,
    )
}

private const val CONFIG_KEY_LENGTH = 32

private object CellIdentityCodec : TlbCodec<Cell> {
    override fun loadTlb(cellSlice: CellSlice): Cell =
        Cell(
            BitString(cellSlice.bits.drop(cellSlice.bitsPosition)),
            *cellSlice.refs.drop(cellSlice.refsPosition).toTypedArray(),
        )

    override fun storeTlb(
        cellBuilder: CellBuilder,
        value: Cell,
    ) {
        cellBuilder.storeBits(value.bits)
        cellBuilder.storeRefs(value.refs)
    }
}
