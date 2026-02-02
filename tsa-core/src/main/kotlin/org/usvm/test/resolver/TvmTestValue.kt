package org.usvm.test.resolver

import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.ton.Endian
import org.usvm.machine.TvmContext
import java.math.BigInteger

@Serializable
sealed interface TvmTestValue

@JvmInline
@Serializable
value class TvmTestIntegerValue(
    val value: @Contextual BigInteger,
) : TvmTestValue

// Artificial entity to represent one-bit integer test values
@JvmInline
@Serializable
value class TvmTestBooleanValue(
    val value: Boolean,
) : TvmTestValue

sealed interface TvmTestReferenceValue

@Serializable
sealed interface TvmTestCellValue :
    TvmTestValue,
    TvmTestReferenceValue

@Serializable
data class TvmTestDictCellValue(
    val keyLength: Int,
    val entries: Map<TvmTestIntegerValue, TvmTestSliceValue>,
) : TvmTestCellValue

@Serializable
data class TvmTestDataCellValue(
    val data: String = "",
    val refs: List<TvmTestCellValue> = listOf(),
    val knownTypes: List<TvmCellDataTypeLoad> = listOf(),
) : TvmTestCellValue {
    fun dataCellDepth(): Int =
        if (refs.isEmpty()) {
            0
        } else {
            val childrenDepths =
                refs.mapNotNull {
                    // null for dict cells
                    (it as? TvmTestDataCellValue)?.dataCellDepth()
                }
            1 + (childrenDepths.maxOrNull() ?: 0)
        }
}

@Serializable
data class TvmTestBuilderValue(
    val data: String,
    val refs: List<TvmTestCellValue>,
) : TvmTestValue,
    TvmTestReferenceValue {
    fun toCell(): TvmTestDataCellValue = TvmTestDataCellValue(data, refs)
}

@Serializable
data class TvmTestTruncatedSliceValue(
    @Required
    val data: String = "",
    @Required
    val refs: List<TvmTestCellValue> = listOf(),
)

private object TruncatedSliceSerializer : KSerializer<TvmTestSliceValue> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        SerialDescriptor(
            "org.usvm.test.resolver.TruncatedSliceSerializer",
            TvmTestTruncatedSliceValue.serializer().descriptor,
        )

    override fun serialize(
        encoder: Encoder,
        value: TvmTestSliceValue,
    ) {
        val presurrogate = truncateSliceCell(value)
        val surrogate = TvmTestTruncatedSliceValue(presurrogate.data, presurrogate.refs)
        return encoder.encodeSerializableValue(TvmTestTruncatedSliceValue.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): TvmTestSliceValue {
        error("Should not be called")
    }
}

@Serializable(with = TruncatedSliceSerializer::class)
data class TvmTestSliceValue(
    val cell: TvmTestDataCellValue = TvmTestDataCellValue(),
    val dataPos: Int = 0,
    val refPos: Int = 0,
) : TvmTestValue,
    TvmTestReferenceValue

@Serializable
data object TvmTestNullValue : TvmTestValue

@Serializable
data class TvmTestTupleValue(
    val elements: List<TvmTestValue>,
) : TvmTestValue

@Serializable
sealed interface TvmTestCellDataTypeRead

@Serializable
data class TvmTestCellDataIntegerRead(
    val bitSize: Int,
    val isSigned: Boolean,
    val endian: Endian,
) : TvmTestCellDataTypeRead

@Serializable
data object TvmTestCellDataMaybeConstructorBitRead : TvmTestCellDataTypeRead

@Serializable
data object TvmTestCellDataMsgAddrRead : TvmTestCellDataTypeRead

@Serializable
data class TvmTestCellDataBitArrayRead(
    val bitSize: Int,
) : TvmTestCellDataTypeRead

@Serializable
data object TvmTestCellDataCoinsRead : TvmTestCellDataTypeRead

@Serializable
data class TvmCellDataTypeLoad(
    val type: TvmTestCellDataTypeRead,
    val offset: Int,
)

/**
 * @param end exclusive
 */
@Serializable
data class CellRange(
    val begin: Int,
    val end: Int,
)

sealed interface TvmTestCellElement {
    val cellRange: CellRange

    data class BitArray(
        val data: String,
        val width: Int,
        val offset: Int,
    ) : TvmTestCellElement {
        override val cellRange: CellRange
            get() = CellRange(offset, offset + width)
    }

    data class Coin(
        val gramsValue: BigInteger,
        val nanogramsWidth: Int,
        val offset: Int,
    ) : TvmTestCellElement {
        override val cellRange: CellRange
            get() = CellRange(offset, offset + 4 + nanogramsWidth * 8)
    }

    data class Integer(
        val value: BigInteger,
        val width: Int,
        val offset: Int,
    ) : TvmTestCellElement {
        override val cellRange: CellRange
            get() = CellRange(offset, offset + width)
    }

    data class MaybeConstructor(
        val begin: Int,
        val isJust: Boolean,
    ) : TvmTestCellElement {
        override val cellRange: CellRange
            get() = CellRange(begin, begin + 1)
    }

    sealed interface AddressRead : TvmTestCellElement {
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

private fun String.strictSubstring(
    begin: Int,
    end: Int,
): String? =
    if (end <= length) {
        substring(begin, end)
    } else {
        null
    }

fun getElements(cell: TvmTestDataCellValue): List<TvmTestCellElement> =
    cell.knownTypes.mapNotNull { (type, offset) ->
        when (type) {
            is TvmTestCellDataBitArrayRead -> {
                val width = type.bitSize
                val data = cell.data.strictSubstring(offset, offset + width) ?: return@mapNotNull null
                TvmTestCellElement.BitArray(data, width, offset)
            }

            TvmTestCellDataCoinsRead -> {
                val actualGramsBegin = offset + 4
                val width = cell.data.strictSubstring(offset, actualGramsBegin)?.toInt(2) ?: return@mapNotNull null
                val value =
                    if (width > 0) {
                        cell.data.strictSubstring(actualGramsBegin, actualGramsBegin + width * 8)?.toBigInteger(2)
                            ?: return@mapNotNull null
                    } else {
                        BigInteger.ZERO
                    }
                TvmTestCellElement.Coin(value, width, offset)
            }

            is TvmTestCellDataIntegerRead -> {
                val width = type.bitSize
                val data =
                    if (width > 0) {
                        cell.data
                            .strictSubstring(offset, offset + width)
                            ?.let {
                                if (type.endian == Endian.LittleEndian) it.reversed() else it
                            }?.toBigInteger(2) ?: return@mapNotNull null
                    } else {
                        BigInteger.ZERO
                    }
                TvmTestCellElement.Integer(data, width, offset)
            }

            TvmTestCellDataMaybeConstructorBitRead -> {
                if (offset < cell.data.length) {
                    TvmTestCellElement.MaybeConstructor(offset, cell.data[offset] == '1')
                } else {
                    null
                }
            }

            TvmTestCellDataMsgAddrRead -> {
                when (val tag = cell.data.substring(offset, offset + 2)) {
                    "00" -> {
                        if (offset + 2 <= cell.data.length) {
                            TvmTestCellElement.AddressRead.None(offset)
                        } else {
                            null
                        }
                    }

                    "10" -> {
                        TvmTestCellElement.AddressRead.Std(
                            offset,
                            cell.data.strictSubstring(
                                offset,
                                offset + TvmTestCellElement.AddressRead.Std.LENGTH_WITH_TAG,
                            ) ?: return@mapNotNull null,
                        )
                    }

                    else -> {
                        error("Unrecognized tag: $tag")
                    }
                }
            }
        }
    }
