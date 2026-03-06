package org.usvm.test.resolver

import kotlinx.serialization.Serializable
import org.ton.Endian
import org.usvm.machine.TvmContext
import java.math.BigInteger

/**
 * @param end exclusive
 */
@Serializable
data class CellRange(
    val begin: Int,
    val end: Int,
)

sealed interface TvmTestCellRead {
    val cellRange: CellRange

    data class BitArray(
        val data: String,
        val width: Int,
        val offset: Int,
    ) : TvmTestCellRead {
        override val cellRange: CellRange
            get() = CellRange(offset, offset + width)
    }

    data class Coin(
        val gramsValue: BigInteger,
        val nanogramsWidth: Int,
        val offset: Int,
    ) : TvmTestCellRead {
        override val cellRange: CellRange
            get() = CellRange(offset, offset + 4 + nanogramsWidth * 8)
    }

    data class Integer(
        val value: BigInteger,
        val width: Int,
        val offset: Int,
        val isSigned: Boolean,
    ) : TvmTestCellRead {
        override val cellRange: CellRange
            get() = CellRange(offset, offset + width)
    }

    data class MaybeConstructor(
        val begin: Int,
        val isJust: Boolean,
    ) : TvmTestCellRead {
        override val cellRange: CellRange
            get() = CellRange(begin, begin + 1)
    }

    sealed interface AddressRead : TvmTestCellRead {
        data class None(
            val offset: Int,
        ) : AddressRead {
            override val cellRange: CellRange
                get() = CellRange(offset, offset + 2)
        }

        data class Std(
            val offset: Int,
            val bits: String,
        ) : AddressRead {
            override val cellRange: CellRange
                get() =
                    CellRange(offset, offset + LENGTH_WITH_TAG)

            companion object {
                val LENGTH_WITH_TAG =
                    TvmContext.ADDRESS_TAG_BITS.toInt() + 1 + TvmContext.STD_WORKCHAIN_BITS +
                        TvmContext.ADDRESS_BITS
            }
        }
    }
}

private fun String.substringSafe(
    begin: Int,
    end: Int,
): String? =
    if (end <= length) {
        substring(begin, end)
    } else {
        null
    }

fun getCellReads(cell: TvmTestDataCellValue): List<TvmTestCellRead> {
    val rawReads =
        cell.knownTypes.mapNotNull { (type, offset) ->
            when (type) {
                is TvmTestCellDataBitArrayRead -> {
                    val width = type.bitSize
                    val data = cell.data.substringSafe(offset, offset + width) ?: return@mapNotNull null
                    TvmTestCellRead.BitArray(data, width, offset)
                }

                TvmTestCellDataCoinsRead -> {
                    val actualGramsBegin = offset + 4
                    val width = cell.data.substringSafe(offset, actualGramsBegin)?.toInt(2) ?: return@mapNotNull null
                    val value =
                        if (width > 0) {
                            cell.data.substringSafe(actualGramsBegin, actualGramsBegin + width * 8)?.toBigInteger(2)
                                ?: return@mapNotNull null
                        } else {
                            BigInteger.ZERO
                        }
                    TvmTestCellRead.Coin(value, width, offset)
                }

                is TvmTestCellDataIntegerRead -> {
                    val width = type.bitSize
                    val data =
                        if (width > 0) {
                            cell.data
                                .substringSafe(offset, offset + width)
                                ?.let {
                                    if (type.endian == Endian.LittleEndian) it.reversed() else it
                                }?.let {
                                    val unsigned = it.toBigInteger(2)
                                    // For signed types, interpret as two's complement:
                                    // if the leading bit is 1, subtract 2^width
                                    if (type.isSigned && it.first() == '1') {
                                        unsigned - BigInteger.valueOf(2).pow(it.length)
                                    } else {
                                        unsigned
                                    }
                                } ?: return@mapNotNull null
                        } else {
                            BigInteger.ZERO
                        }
                    TvmTestCellRead.Integer(data, width, offset, isSigned = type.isSigned)
                }

                TvmTestCellDataMaybeConstructorBitRead -> {
                    if (offset < cell.data.length) {
                        TvmTestCellRead.MaybeConstructor(offset, cell.data[offset] == '1')
                    } else {
                        null
                    }
                }

                TvmTestCellDataMsgAddrRead -> {
                    when (val tag = cell.data.substringSafe(offset, offset + 2)) {
                        "00" -> {
                            if (offset + 2 <= cell.data.length) {
                                TvmTestCellRead.AddressRead.None(offset)
                            } else {
                                null
                            }
                        }

                        "10" -> {
                            TvmTestCellRead.AddressRead.Std(
                                offset,
                                cell.data.substringSafe(
                                    offset,
                                    offset + TvmTestCellRead.AddressRead.Std.LENGTH_WITH_TAG,
                                ) ?: return@mapNotNull null,
                            )
                        }

                        null -> {
                            null
                        }

                        else -> {
                            error("Unrecognized tag: $tag")
                        }
                    }
                }
            }
        }

    return normalizeCellReads(cell, rawReads)
}

private fun TvmTestCellRead.priority(): Int =
    when (this) {
        is TvmTestCellRead.BitArray -> 0
        is TvmTestCellRead.AddressRead, is TvmTestCellRead.MaybeConstructor -> 1
        is TvmTestCellRead.Integer -> 2
        is TvmTestCellRead.Coin -> 3
    }

private fun normalizeCellReads(
    cell: TvmTestDataCellValue,
    reads: List<TvmTestCellRead>,
): List<TvmTestCellRead> {
    val nonBitArrayReads = reads.filter { it !is TvmTestCellRead.BitArray }

    val points =
        (
            listOf(0, cell.data.length) +
                nonBitArrayReads.flatMap { listOf(it.cellRange.begin, it.cellRange.end) }
        ).toSet()
            .sorted()

    return (points.dropLast(1) zip points.drop(1)).map { (begin, end) ->
        val bestExactRead =
            reads
                .filter {
                    it.cellRange == CellRange(begin, end)
                }.maxByOrNull { it.priority() }

        bestExactRead ?: TvmTestCellRead.BitArray(
            cell.data.substring(begin, end),
            end - begin,
            begin,
        )
    }
}
