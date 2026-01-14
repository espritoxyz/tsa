package org.ton.examples.cell

import org.ton.Endian
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.usvm.test.resolver.TvmCellDataTypeLoad
import org.usvm.test.resolver.TvmTestCellDataBitArrayRead
import org.usvm.test.resolver.TvmTestCellDataCoinsRead
import org.usvm.test.resolver.TvmTestCellDataIntegerRead
import org.usvm.test.resolver.TvmTestCellDataMaybeConstructorBitRead
import org.usvm.test.resolver.TvmTestCellDataMsgAddrRead
import org.usvm.test.resolver.TvmTestCellElement
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.getElements
import kotlin.test.Test
import kotlin.test.assertEquals

class CellParserTest {
    @Test
    fun `single integer`() {
        baseTest(
            cellUnderTest = CellBuilder().storeInt(3, 5).endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(5, false, Endian.LittleEndian), 0),
                ),
            expected = listOf<TvmTestCellElement>(TvmTestCellElement.Integer(3, 5)),
        )
    }

    @Test
    fun `two integers`() {
        baseTest(
            cellUnderTest = CellBuilder().storeInt(3, 5).storeInt(7, 19).endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(5, false, Endian.LittleEndian), 0),
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(19, false, Endian.LittleEndian), 5),
                ),
            expected = listOf<TvmTestCellElement>(TvmTestCellElement.Integer(3, 5), TvmTestCellElement.Integer(7, 19)),
        )
    }

    @Test
    fun `three integers`() {
        baseTest(
            cellUnderTest =
                CellBuilder()
                    .storeInt(3, 5)
                    .storeInt(7, 19)
                    .storeInt(9, 6)
                    .endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(5, false, Endian.LittleEndian), 0),
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(19, false, Endian.LittleEndian), 5),
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(6, false, Endian.LittleEndian), 5 + 19),
                ),
            expected =
                listOf<TvmTestCellElement>(
                    TvmTestCellElement.Integer(3, 5),
                    TvmTestCellElement.Integer(7, 19),
                    TvmTestCellElement.Integer(9, 6),
                ),
        )
    }

    private fun CellBuilder.storeCoin(
        value: Int,
        width: Int,
    ): CellBuilder = storeUInt(width, 16).storeUInt(value, width)

    @Test
    fun `single coin`() {
        baseTest(
            cellUnderTest = CellBuilder().storeCoin(5, 4).endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataCoinsRead, 0),
                ),
            expected = listOf<TvmTestCellElement>(TvmTestCellElement.Coin(5, 4)),
        )
    }

    @Test
    fun `two coins`() {
        baseTest(
            cellUnderTest = CellBuilder().storeCoin(5, 4).storeCoin(13, 4).endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataCoinsRead, 0),
                    TvmCellDataTypeLoad(TvmTestCellDataCoinsRead, 20),
                ),
            expected = listOf<TvmTestCellElement>(TvmTestCellElement.Coin(5, 4), TvmTestCellElement.Coin(13, 4)),
        )
    }

    @Test
    fun `integer and coin`() {
        baseTest(
            cellUnderTest = CellBuilder().storeInt(4, 5).storeCoin(13, 4).endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(5, true, Endian.LittleEndian), 0),
                    TvmCellDataTypeLoad(TvmTestCellDataCoinsRead, 5),
                ),
            expected = listOf<TvmTestCellElement>(TvmTestCellElement.Integer(4, 5), TvmTestCellElement.Coin(13, 4)),
        )
    }

    @Test
    fun bitarray() {
        baseTest(
            cellUnderTest = CellBuilder().storeBits("0010").endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataBitArrayRead(4), 0),
                ),
            expected = listOf<TvmTestCellElement>(TvmTestCellElement.BitArray("0010", 4)),
        )
    }

    @Test
    fun `bitarray and maybe constructor`() {
        baseTest(
            cellUnderTest = CellBuilder().storeBits("00101").endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataBitArrayRead(4), 0),
                    TvmCellDataTypeLoad(TvmTestCellDataMaybeConstructorBitRead, 4),
                ),
            expected =
                listOf<TvmTestCellElement>(
                    TvmTestCellElement.BitArray("0010", 4),
                    TvmTestCellElement.MaybeConstructor,
                ),
        )
    }

    private fun CellBuilder.storeNoneAddress(): CellBuilder = storeBits("00")

    @Test
    fun address() {
        baseTest(
            cellUnderTest = CellBuilder().storeNoneAddress().endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataMsgAddrRead, 0),
                ),
            expected =
                listOf<TvmTestCellElement>(
                    TvmTestCellElement.AddressRead,
                ),
        )
    }

    /**
     * Note: while this technically implies a three-times repetition, it was decided to keep as to not introduce
     * another hierarchy of similar types specifically for tests.
     */
    private fun baseTest(
        cellUnderTest: Cell,
        knownTypes: List<TvmCellDataTypeLoad>,
        expected: List<TvmTestCellElement>,
    ) {
        val dataBits = cellUnderTest.bits.toBinary()
        val testCell =
            TvmTestDataCellValue(dataBits, listOf(), knownTypes)
        val elems = getElements(testCell)
        assertEquals(expected, elems)
    }

    private fun String.asBits() = map { it == '1' }

    private fun CellBuilder.storeBits(strBits: String) = storeBits(strBits.asBits())
}
