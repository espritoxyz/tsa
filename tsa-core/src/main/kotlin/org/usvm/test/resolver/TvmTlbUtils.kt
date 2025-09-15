package org.usvm.test.resolver

import org.ton.bitstring.BitString
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.CellSlice
import org.ton.hashmap.HashMapE
import org.ton.tlb.TlbCodec
import java.math.BigInteger

// TODO maybe use HashMapESerializer from `ton-disassembler` after making it public
data object HashMapESerializer : TlbCodec<Cell> {
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

fun transformTestCellIntoCell(value: TvmTestCellValue): Cell =
    when (value) {
        is TvmTestDataCellValue -> transformTestDataCellIntoCell(value)
        is TvmTestDictCellValue -> transformTestDictCellIntoCell(value)
    }

fun transformTestDataCellIntoCell(value: TvmTestDataCellValue): Cell {
    val refs = value.refs.map(::transformTestCellIntoCell)
    val binaryData = BitString(value.data.map { it == '1' })
    return Cell(binaryData, *refs.toTypedArray())
}

fun transformMapIntoHashMapE(
    keyLength: Int,
    map: Map<BigInteger, Cell>,
): HashMapE<Cell> {
    val patchedContent =
        map.entries.associate { (key, entryValue) ->
            val normalizedKey = key.toUnsignedDictKey(keyLength)
            val keyPadded = normalizedKey.toString(2).padStart(length = keyLength, padChar = '0')
            val bitArray = keyPadded.map { it == '1' }

            BitString(bitArray) to entryValue
        }
    return HashMapE.fromMap(patchedContent)
}

fun transformTestDictCellIntoHashMapE(value: TvmTestDictCellValue): HashMapE<Cell> {
    val patchedMap =
        value.entries.entries.associate { (key, slice) ->
            key.value to transformTestDataCellIntoCell(truncateSliceCell(slice))
        }

    return transformMapIntoHashMapE(value.keyLength, patchedMap)
}

fun transformTestDictCellIntoCell(value: TvmTestDictCellValue): Cell {
    val hashMap = transformTestDictCellIntoHashMapE(value)
    val codec = HashMapE.tlbCodec(value.keyLength, HashMapESerializer)
    val cellForHashMapE = codec.createCell(hashMap)
    check(cellForHashMapE.bits.single() && cellForHashMapE.refs.size == 1)
    return cellForHashMapE.refs.single()
}

private fun BigInteger.toUnsignedDictKey(keyLength: Int) = this.mod(BigInteger.ONE.shiftLeft(keyLength))
