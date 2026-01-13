package org.ton.bytecode

import kotlinx.serialization.json.Json
import org.ton.boc.BagOfCells
import org.usvm.machine.toTvmCell
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readBytes
import kotlin.io.path.readText

data class TsaContractCode(
    val mainMethod: TvmMainMethod,
    val methods: Map<MethodId, TvmMethod>,
    val codeCell: TvmCell,
    val parentCode: TsaContractCode? = null,
) {
    var isContractWithTSACheckerFunctions: Boolean = false

    companion object {
        @OptIn(ExperimentalStdlibApi::class)
        fun construct(bocFilePath: Path): TsaContractCode {
            val bocAsByteArray =
                when (bocFilePath.extension) {
                    "json" -> {
                        val json = Json.decodeFromString<Map<String, String>>(bocFilePath.readText())
                        val hexString =
                            json["hex"]
                                ?: error("Bad json in boc file $bocFilePath: no 'hex' key at the top level")
                        hexString.hexToByteArray()
                    }

                    else -> {
                        bocFilePath.readBytes()
                    }
                }
            val cell = BagOfCells(bocAsByteArray).roots.first().toTvmCell()
            val tvmContractCode = disassembleBoc(bocAsByteArray)
            return construct(tvmContractCode, cell, parentCode = null)
        }

        fun construct(
            tvmContractCode: TvmContractCode,
            cell: TvmCell,
            parentCode: TsaContractCode?,
        ): TsaContractCode {
            val newMethods =
                tvmContractCode.methods.entries.associate { (key, value) ->
                    key to value.addReturnStmt()
                }
            val newMainMethod = tvmContractCode.mainMethod.addReturnStmt()
            return TsaContractCode(
                mainMethod = newMainMethod,
                methods = newMethods,
                codeCell = cell,
                parentCode = parentCode,
            )
        }
    }
}

fun setTSACheckerFunctions(contractCode: TsaContractCode) {
    contractCode.isContractWithTSACheckerFunctions = true
}
