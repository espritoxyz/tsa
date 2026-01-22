package org.usvm.test.resolver

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ReadData {
    @Serializable
    @SerialName("Integer")
    data class Integer(
        val value: String,
    ) : ReadData

    @Serializable
    @SerialName("MaybeConstructor")
    data class MaybeCtorBit(
        val isJust: Boolean,
    ) : ReadData

    @Serializable
    @SerialName("AddressRead")
    data class AddressRead(
        val rawValue: String,
    ) : ReadData

    @Serializable
    @SerialName("Coin")
    data class Coin(
        val grams: String,
    ) : ReadData

    @Serializable
    @SerialName("BitArray")
    data object BitArray : ReadData

    @Serializable
    data object Untyped : ReadData
}

@Serializable
data class Read(
    val range: CellRange, // = listOf(begin, end)
    val rawBits: String,
    val readData: ReadData,
)

@Serializable
sealed interface PrettyCell {
    @Serializable
    @SerialName("DataCell")
    data class DataCell(
        val reads: List<Read>,
        val otherCells: List<PrettyCell>,
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
        getElements(this)
            .map {
                val readData: ReadData =
                    when (it) {
                        is TvmTestCellElement.AddressRead.None -> {
                            ReadData.AddressRead("none")
                        }

                        is TvmTestCellElement.AddressRead.Std -> {
                            ReadData.AddressRead(
                                stdAddressBitsToRawAddress(
                                    this@toPrettyCell.data.substring(
                                        it.cellRange.begin,
                                        it.cellRange.end,
                                    ),
                                ),
                            )
                        }

                        is TvmTestCellElement.BitArray -> {
                            ReadData.BitArray
                        }

                        is TvmTestCellElement.Coin -> {
                            ReadData.Coin(it.gramsValue.toString())
                        }

                        is TvmTestCellElement.Integer -> {
                            ReadData.Integer(it.value.toString())
                        }

                        is TvmTestCellElement.MaybeConstructor -> {
                            ReadData.MaybeCtorBit(it.isJust)
                        }
                    }
                Read(
                    it.cellRange,
                    data.substring(it.cellRange.begin, it.cellRange.end),
                    readData,
                )
            }.sortedBy {
                // put untyped data in the end
                when (it.readData) {
                    ReadData.BitArray -> 1
                    ReadData.Untyped -> 1
                    else -> 0
                }
            }.sortedBy { it.range.begin }
    return PrettyCell.DataCell(reads, refs.map { it.toPrettyCell() })
}

fun TvmTestCellValue.toPrettyYaml(): String =
    Yaml(
        configuration =
            YamlConfiguration(),
    ).encodeToString(PrettyCell.serializer(), toPrettyCell())
