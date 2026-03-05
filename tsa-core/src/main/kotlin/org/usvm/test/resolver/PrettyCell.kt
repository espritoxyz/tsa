package org.usvm.test.resolver

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigInteger

@Serializable
sealed interface CellElement {
    val length: Int

    @Serializable
    @SerialName("int")
    data class SignedInteger(
        val value: String,
        override val length: Int,
    ) : CellElement

    @Serializable
    @SerialName("uint")
    data class UnsignedInteger(
        val value: String,
        override val length: Int,
    ) : CellElement

    @Serializable
    @SerialName("address")
    data class Address(
        val rawValue: String,
        override val length: Int,
    ) : CellElement

    @Serializable
    @SerialName("coins")
    data class Coins(
        val grams: String,
        override val length: Int,
    ) : CellElement

    @Serializable
    @SerialName("bits")
    data class BitArray(
        override val length: Int,
        val data: String,
    ) : CellElement
}

@Serializable
sealed interface PrettyCell {
    @Serializable
    @SerialName("DataCell")
    data class DataCell(
        val elements: List<CellElement>,
        val refs: List<PrettyCell>,
    ) : PrettyCell

    @Serializable
    @SerialName("DictCell")
    object DictCell : PrettyCell
}

fun TvmTestCellValue.toPrettyCell(): PrettyCell =
    when (this) {
        is TvmTestDataCellValue -> toPrettyCell()
        is TvmTestDictCellValue -> PrettyCell.DictCell
    }

private fun stdAddressBitsToRawAddress(rawAddress: String): String {
    val tag = rawAddress.substring(0..<2)
    require(tag == "10") { "not an std address" }
    val addrBegin = 3 + 8
    val workchainID = rawAddress.substring(3..<addrBegin).toInt(2).toString()
    val addr =
        rawAddress
            .substring((addrBegin..<addrBegin + 256))
            .toBigInteger(2)
            .toString(16)
            .padStart(64, '0')
    return "$workchainID:$addr"
}

fun TvmTestDataCellValue.toPrettyCell(): PrettyCell {
    val reads =
        getCellReads(this)
            .sortedBy { it.cellRange.begin }
            .map {
                val length = it.cellRange.end - it.cellRange.begin

                val readData: CellElement =
                    when (it) {
                        is TvmTestCellRead.AddressRead.None -> {
                            CellElement.Address("none", length)
                        }

                        is TvmTestCellRead.AddressRead.Std -> {
                            CellElement.Address(
                                stdAddressBitsToRawAddress(
                                    this@toPrettyCell.data.substring(
                                        it.cellRange.begin,
                                        it.cellRange.end,
                                    ),
                                ),
                                length,
                            )
                        }

                        is TvmTestCellRead.BitArray -> {
                            CellElement.BitArray(length, it.data)
                        }

                        is TvmTestCellRead.Coin -> {
                            CellElement.Coins(it.gramsValue.toString(), length)
                        }

                        is TvmTestCellRead.Integer -> {
                            if (it.isSigned) {
                                CellElement.SignedInteger(it.value.toString(), length)
                            } else {
                                check(it.value >= BigInteger.ZERO) {
                                    "Unexpected unsigned int: ${it.value}"
                                }
                                CellElement.UnsignedInteger(it.value.toString(), length)
                            }
                        }

                        is TvmTestCellRead.MaybeConstructor -> {
                            CellElement.UnsignedInteger(if (it.isJust) "1" else "0", length)
                        }
                    }

                readData
            }
    return PrettyCell.DataCell(reads, refs.map { it.toPrettyCell() })
}

fun TvmTestCellValue.toPrettyYaml(): String =
    Yaml(
        configuration =
            YamlConfiguration(polymorphismStyle = PolymorphismStyle.Property),
    ).encodeToString(PrettyCell.serializer(), toPrettyCell())
