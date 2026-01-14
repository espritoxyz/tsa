package org.ton.options

import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.OptionCallTransformContext
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.transformValues
import org.ton.bytecode.TsaContractCode
import org.usvm.machine.BocAnalyzer
import org.usvm.machine.FiftAnalyzer
import org.usvm.machine.FuncAnalyzer
import org.usvm.machine.TactAnalyzer
import org.usvm.machine.TactSourcesDescription
import java.nio.file.Path

enum class ContractType {
    Tact,
    Func,
    Fift,
    Boc,
}

sealed interface ContractSources {
    fun convertToTsaContractCode(
        fiftAnalyzer: FiftAnalyzer,
        funcAnalyzer: FuncAnalyzer,
        tactAnalyzer: TactAnalyzer,
    ): TsaContractCode
}

data class SinglePath(
    val type: ContractType,
    val path: Path,
) : ContractSources {
    override fun convertToTsaContractCode(
        fiftAnalyzer: FiftAnalyzer,
        funcAnalyzer: FuncAnalyzer,
        tactAnalyzer: TactAnalyzer,
    ): TsaContractCode {
        val analyzer =
            when (type) {
                ContractType.Boc -> BocAnalyzer
                ContractType.Func -> funcAnalyzer
                ContractType.Fift -> fiftAnalyzer
                ContractType.Tact -> error("Unexpected contract type $type with a single path $path")
            }

        return analyzer.convertToTvmContractCode(path)
    }
}

data class TactPath(
    val tactPath: TactSourcesDescription,
) : ContractSources {
    override fun convertToTsaContractCode(
        fiftAnalyzer: FiftAnalyzer,
        funcAnalyzer: FuncAnalyzer,
        tactAnalyzer: TactAnalyzer,
    ): TsaContractCode = tactAnalyzer.convertToTvmContractCode(tactPath)
}

fun ParameterHolder.contractSourcesOption(
    pathOptionDescriptor: NullableOption<Path, Path>,
    typeOptionDescriptor: NullableOption<ContractType, ContractType>,
    pathValidator: OptionCallTransformContext.(Path) -> Unit = { },
) = option("-c", "--contract")
    .help(
        """
        Contract to analyze. Must be given in format <contract-type> <options>.
        
        <contract-type> can be Tact, Func, Fift or Boc.
        
        For Func, Fift and Boc <options> is path to contract sources.
        
        For Tact, <options> is three values separated by space:
        <path to tact.config.json> <project name> <contract name>
        
        This option should be used for each analyzed contract separately.
        
        Examples:
        
        -c func jetton-wallet.fc
        
        -c tact path/to/tact.config.json Jetton JettonWallet
        
        """.trimIndent(),
    ).transformValues(nvalues = 2..4) { args ->
        val typeRaw = args[0]
        val type = typeOptionDescriptor.transformValue(this, typeRaw)
        val pathRaw = args[1]
        val path = pathOptionDescriptor.transformValue(this, pathRaw).also { pathValidator(it) }
        if (type == ContractType.Tact) {
            require(args.size == 4) {
                "Tact expects 3 parameters: path to tact.config.json, project name, contract name."
            }
            val taskName = args[2]
            val contractName = args[3]
            TactPath(TactSourcesDescription(path, taskName, contractName))
        } else {
            require(args.size == 2) {
                "Func, Fift and Boc expect only 1 parameter: path to contract source."
            }
            SinglePath(type, path)
        }
    }
