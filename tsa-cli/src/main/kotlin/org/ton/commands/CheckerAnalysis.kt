package org.ton.commands

import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import org.ton.bytecode.TsaContractCode
import org.ton.options.ContractSources
import org.ton.options.ContractType
import org.ton.options.FiftOptions
import org.ton.options.TactOptions
import org.ton.options.contractSourcesOption
import org.usvm.machine.FiftAnalyzer
import org.usvm.machine.FuncAnalyzer
import org.usvm.machine.TactAnalyzer
import org.usvm.machine.getFuncContract

class CheckerAnalysis : AbstractCheckerAnalysis("custom-checker") {
    private val checkerContractPath by option("--checker")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .help("The path to the checker contract (in FunC).")
        .required()

    private val fiftOptions by FiftOptions()

    private val fiftAnalyzer by lazy {
        FiftAnalyzer(
            fiftStdlibPath = fiftOptions.fiftStdlibPath,
        )
    }

    private val funcAnalyzer by lazy {
        FuncAnalyzer(
            fiftStdlibPath = fiftOptions.fiftStdlibPath,
        )
    }

    private val tactOptions by TactOptions()

    private val tactAnalyzer by lazy {
        TactAnalyzer(
            tactExecutable = tactOptions.tactExecutable,
        )
    }

    private val typeOptionDescriptor = option().enum<ContractType>(ignoreCase = true)
    private val contractSources: List<ContractSources> by contractSourcesOption(
        pathOptionDescriptor,
        typeOptionDescriptor,
    ).multiple(required = true)
        .validate {
            if (it.size > 1) {
                requireNotNull(interContractSchemePath) {
                    "Inter-contract communication scheme is required for multiple contracts"
                }
            }
        }

    override val checkerContract: TsaContractCode by lazy {
        getFuncContract(
            checkerContractPath,
            fiftOptions.fiftStdlibPath,
            isTSAChecker = true,
        )
    }

    override val contractsToAnalyze: List<TsaContractCode> by lazy {
        contractSources.map {
            it.convertToTsaContractCode(fiftAnalyzer, funcAnalyzer, tactAnalyzer)
        }
    }
}
