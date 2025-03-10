package org.usvm.test.resolver

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.ton.Endian
import java.math.BigInteger

@Serializable
sealed interface TvmTestValue

@JvmInline
@Serializable
value class TvmTestIntegerValue(
    val value: @Contextual BigInteger
): TvmTestValue

// Artificial entity to represent one-bit integer test values
@JvmInline
@Serializable
value class TvmTestBooleanValue(
    val value: Boolean
): TvmTestValue

sealed interface TvmTestReferenceValue

@Serializable
sealed interface TvmTestCellValue: TvmTestValue, TvmTestReferenceValue

@Serializable
data class TvmTestDictCellValue(
    val keyLength: Int,
    val entries: Map<TvmTestIntegerValue, TvmTestSliceValue>,
): TvmTestCellValue

@Serializable
data class TvmTestDataCellValue(
    val data: String = "",
    val refs: List<TvmTestCellValue> = listOf(),
    val knownTypes: List<TvmCellDataTypeLoad> = listOf()
): TvmTestCellValue {
    fun dataCellDepth(): Int =
        if (refs.isEmpty()) {
            0
        } else {
            val childrenDepths = refs.mapNotNull {
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
): TvmTestValue, TvmTestReferenceValue

@Serializable
data class TvmTestSliceValue(
    val cell: TvmTestDataCellValue = TvmTestDataCellValue(),
    val dataPos: Int = 0,
    val refPos: Int = 0,
): TvmTestValue, TvmTestReferenceValue

@Serializable
data object TvmTestNullValue: TvmTestValue

@Serializable
data class TvmTestTupleValue(
    val elements: List<TvmTestValue>
) : TvmTestValue

@Serializable
sealed interface TvmTestCellDataTypeRead

@Serializable
data class TvmTestCellDataIntegerRead(val bitSize: Int, val isSigned: Boolean, val endian: Endian): TvmTestCellDataTypeRead

@Serializable
data object TvmTestCellDataMaybeConstructorBitRead: TvmTestCellDataTypeRead

@Serializable
data object TvmTestCellDataMsgAddrRead : TvmTestCellDataTypeRead

@Serializable
data class TvmTestCellDataBitArrayRead(val bitSize: Int): TvmTestCellDataTypeRead

@Serializable
data object TvmTestCellDataCoinsRead : TvmTestCellDataTypeRead

@Serializable
data class TvmCellDataTypeLoad(
    val type: TvmTestCellDataTypeRead,
    val offset: Int
)
