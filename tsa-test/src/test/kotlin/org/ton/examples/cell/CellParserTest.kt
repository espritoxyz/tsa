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
        baseTestWithFullCellCoverage(
            cellUnderTest = CellBuilder().storeInt(3, 5).endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(5, false, Endian.BigEndian), 0),
                ),
            expected =
                listOf<TvmTestCellElement>(
                    TvmTestCellElement.Integer(
                        value = 3.toBigInteger(),
                        width = 5,
                        offset = 0,
                    ),
                ),
        )
    }

    @Test
    fun `empty integer`() {
        baseTestWithFullCellCoverage(
            cellUnderTest = CellBuilder().endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(0, false, Endian.BigEndian), 0),
                ),
            expected =
                listOf<TvmTestCellElement>(
                    TvmTestCellElement.Integer(
                        value = 0.toBigInteger(),
                        width = 0,
                        offset = 0,
                    ),
                ),
        )
    }

    @Test
    fun `single integer underflow`() {
        baseTestWithFullCellCoverage(
            cellUnderTest = CellBuilder().storeInt(3, 5).endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(5, false, Endian.BigEndian), 1),
                ),
            expected = listOf(), // no successful reads
        )
    }

    @Test
    fun `two integers`() {
        baseTestWithFullCellCoverage(
            cellUnderTest = CellBuilder().storeInt(3, 5).storeInt(7, 19).endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(5, false, Endian.BigEndian), 0),
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(19, false, Endian.BigEndian), 5),
                ),
            expected =
                listOf<TvmTestCellElement>(
                    TvmTestCellElement.Integer(value = 3.toBigInteger(), width = 5, offset = 0),
                    TvmTestCellElement.Integer(value = 7.toBigInteger(), width = 19, offset = 5),
                ),
        )
    }

    @Test
    fun `three integers`() {
        baseTestWithFullCellCoverage(
            cellUnderTest =
                CellBuilder()
                    .storeInt(3, 5)
                    .storeInt(7, 19)
                    .storeInt(9, 6)
                    .endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(5, false, Endian.BigEndian), 0),
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(19, false, Endian.BigEndian), 5),
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(6, false, Endian.BigEndian), 5 + 19),
                ),
            expected =
                listOf<TvmTestCellElement>(
                    TvmTestCellElement.Integer(value = 3.toBigInteger(), width = 5, offset = 0),
                    TvmTestCellElement.Integer(value = 7.toBigInteger(), width = 19, offset = 5),
                    TvmTestCellElement.Integer(value = 9.toBigInteger(), width = 6, offset = 5 + 19),
                ),
        )
    }

    private fun CellBuilder.storeCoin(
        value: Int,
        nanogramsWidthDivBy8: Int,
    ): CellBuilder = storeUInt(nanogramsWidthDivBy8, 4).storeUInt(value, 8 * nanogramsWidthDivBy8)

    @Test
    fun `single coin`() {
        baseTestWithFullCellCoverage(
            cellUnderTest = CellBuilder().storeCoin(5, 4).endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataCoinsRead, 0),
                ),
            expected =
                listOf<TvmTestCellElement>(
                    TvmTestCellElement.Coin(gramsValue = 5.toBigInteger(), nanogramsWidth = 4, offset = 0),
                ),
        )
    }

    @Test
    fun `empty coin`() {
        baseTestWithFullCellCoverage(
            cellUnderTest = CellBuilder().storeCoin(0, 0).endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataCoinsRead, 0),
                ),
            expected =
                listOf<TvmTestCellElement>(
                    TvmTestCellElement.Coin(gramsValue = 0.toBigInteger(), nanogramsWidth = 0, offset = 0),
                ),
        )
    }

    @Test
    fun `two coins`() {
        baseTestWithFullCellCoverage(
            cellUnderTest = CellBuilder().storeCoin(5, 1).storeCoin(13, 1).endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataCoinsRead, 0),
                    TvmCellDataTypeLoad(TvmTestCellDataCoinsRead, 12),
                ),
            expected =
                listOf<TvmTestCellElement>(
                    TvmTestCellElement.Coin(gramsValue = 5.toBigInteger(), nanogramsWidth = 1, offset = 0),
                    TvmTestCellElement.Coin(gramsValue = 13.toBigInteger(), nanogramsWidth = 1, offset = 12),
                ),
        )
    }

    @Test
    fun `integer and coin`() {
        baseTestWithFullCellCoverage(
            cellUnderTest = CellBuilder().storeInt(4, 5).storeCoin(13, 2).endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataIntegerRead(5, true, Endian.BigEndian), 0),
                    TvmCellDataTypeLoad(TvmTestCellDataCoinsRead, 5),
                ),
            expected =
                listOf(
                    TvmTestCellElement.Integer(value = 4.toBigInteger(), width = 5, offset = 0),
                    TvmTestCellElement.Coin(gramsValue = 13.toBigInteger(), nanogramsWidth = 2, offset = 5),
                ),
        )
    }

    @Test
    fun bitarray() {
        baseTestWithFullCellCoverage(
            cellUnderTest = CellBuilder().storeBits("0010").endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataBitArrayRead(4), 0),
                ),
            expected = listOf<TvmTestCellElement>(TvmTestCellElement.BitArray("0010", 4, 0)),
        )
    }

    @Test
    fun `bitarray and maybe constructor`() {
        baseTestWithFullCellCoverage(
            cellUnderTest = CellBuilder().storeBits("00101").endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataBitArrayRead(4), 0),
                    TvmCellDataTypeLoad(TvmTestCellDataMaybeConstructorBitRead, 4),
                ),
            expected =
                listOf(
                    TvmTestCellElement.BitArray(data = "0010", width = 4, offset = 0),
                    TvmTestCellElement.MaybeConstructor(begin = 4, isJust = true),
                ),
        )
    }

    private fun CellBuilder.storeNoneAddress(): CellBuilder = storeBits("00")

    @Test
    fun addressNone() {
        baseTestWithFullCellCoverage(
            cellUnderTest = CellBuilder().storeNoneAddress().endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataMsgAddrRead, 0),
                ),
            expected =
                listOf<TvmTestCellElement>(
                    TvmTestCellElement.AddressRead.None(0),
                ),
        )
    }

    @Test
    fun addressNoneAndStd() {
        val addressStd = "10" + "0" + "0".repeat(8) + "0".repeat(256)
        baseTestWithFullCellCoverage(
            cellUnderTest = CellBuilder().storeNoneAddress().storeBits(addressStd).endCell(),
            knownTypes =
                listOf(
                    TvmCellDataTypeLoad(TvmTestCellDataMsgAddrRead, 0),
                    TvmCellDataTypeLoad(TvmTestCellDataMsgAddrRead, 2),
                ),
            expected =
                listOf<TvmTestCellElement>(
                    TvmTestCellElement.AddressRead.None(0),
                    TvmTestCellElement.AddressRead.Std(2, addressStd),
                ),
        )
    }

    /**
     * Note: while this technically implies a three-times repetition, it was decided to keep as to not introduce
     * another hierarchy of similar types specifically for tests.
     */
    private fun baseTestWithFullCellCoverage(
        cellUnderTest: Cell,
        knownTypes: List<TvmCellDataTypeLoad>,
        expected: List<TvmTestCellElement>,
    ) {
        val dataBits = cellUnderTest.bits.toBinary()
        val testCell =
            TvmTestDataCellValue(dataBits, listOf(), knownTypes)
        val elems = getElements(testCell)
        assertEquals(expected, elems)
        if (elems.isNotEmpty()) {
            assertEquals(0, elems.first().cellRange.begin)
            for ((cur, next) in elems.windowed(2)) {
                assertEquals(cur.cellRange.end, next.cellRange.begin)
            }
        }
    }

    private fun String.asBits() = map { it == '1' }

    private fun CellBuilder.storeBits(strBits: String) = storeBits(strBits.asBits())
}
