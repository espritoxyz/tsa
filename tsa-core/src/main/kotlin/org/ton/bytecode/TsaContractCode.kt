package org.ton.bytecode

import org.ton.boc.BagOfCells
import org.usvm.machine.toTvmCell
import java.nio.file.Path
import kotlin.io.path.readBytes

data class TsaContractCode(
    val mainMethod: TvmMainMethod,
    val methods: Map<MethodId, TvmMethod>,
    val codeCell: TvmCell,
    val parentCode: TsaContractCode? = null,
) {
    var isContractWithTSACheckerFunctions: Boolean = false

    companion object {
        fun construct(bocFilePath: Path): TsaContractCode {
            val bocAsByteArray = bocFilePath.readBytes()
            val cell = BagOfCells(bocAsByteArray).roots.first().toTvmCell()
            val tvmContractCode = disassembleBoc(bocFilePath)
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
                parentCode = parentCode
            )
        }
    }
}

fun setTSACheckerFunctions(contractCode: TsaContractCode) {
    contractCode.isContractWithTSACheckerFunctions = true
}
