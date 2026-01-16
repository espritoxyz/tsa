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
    TvmTestReferenceValue

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
        val gramsValue: Int,
        val nanogramsWidth: Int,
        val offset: Int,
    ) : TvmTestCellElement {
        override val cellRange: CellRange
            get() = CellRange(offset, offset + 4 + nanogramsWidth * 8)
    }

    data class Integer(
        val value: Int,
        val width: Int,
        val offset: Int,
    ) : TvmTestCellElement {
        override val cellRange: CellRange
            get() = CellRange(offset, offset + width)
    }

    data class MaybeConstructor(
        val begin: Int,
    ) : TvmTestCellElement {
        override val cellRange: CellRange
            get() = CellRange(begin, begin + 1)
    }

    enum class AddressKind {
        NONE,
        STD,
    }

    data class AddressRead(
        val kind: AddressKind,
        val offset: Int,
    ) : TvmTestCellElement {
        override val cellRange: CellRange
            get() {
                val width =
                    when (kind) {
                        AddressKind.NONE -> 2
                        AddressKind.STD -> 2 + 1 + 8 + 256
                    }
                return CellRange(offset, offset + width)
            }
    }
}

fun getElements(cell: TvmTestDataCellValue): List<TvmTestCellElement> =
    cell.knownTypes.map { (type, offset) ->
        when (type) {
            is TvmTestCellDataBitArrayRead -> {
                val width = type.bitSize
                val data = cell.data.substring(offset, offset + width)
                TvmTestCellElement.BitArray(data, width, offset)
            }

            TvmTestCellDataCoinsRead -> {
                val actualGramsBegin = offset + 4
                val width = cell.data.substring(offset, actualGramsBegin).toInt(2)
                val value = cell.data.substring(actualGramsBegin, actualGramsBegin + width * 8).toInt(2)
                TvmTestCellElement.Coin(value, width, offset)
            }

            is TvmTestCellDataIntegerRead -> {
                val width = type.bitSize
                val data =
                    cell.data
                        .substring(offset, offset + width)
                        .let {
                            if (type.endian == Endian.BigEndian) it.reversed() else it
                        }.toInt(2)
                TvmTestCellElement.Integer(data, width, offset)
            }

            TvmTestCellDataMaybeConstructorBitRead -> {
                TvmTestCellElement.MaybeConstructor(offset)
            }

            TvmTestCellDataMsgAddrRead -> {
                // TODO parse the address to include the pretty-printed address
                val tag = cell.data.substring(offset, offset + 2)
                val kind =
                    when (tag) {
                        "00" -> TvmTestCellElement.AddressKind.NONE
                        "10" -> TvmTestCellElement.AddressKind.STD
                        else -> error("Unrecognized tag: $tag")
                    }
                TvmTestCellElement.AddressRead(kind, offset)
            }
        }
    }
