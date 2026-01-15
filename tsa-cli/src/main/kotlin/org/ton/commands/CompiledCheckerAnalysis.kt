package org.ton.commands

import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.ton.bytecode.TsaContractCode
import org.usvm.machine.BocAnalyzer

class CompiledCheckerAnalysis : AbstractCheckerAnalysis("custom-checker-compiled") {
    override val checkerContract: TsaContractCode
    override val contractsToAnalyze: List<TsaContractCode>

    private val checkerContractPath by option("--checker")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .help("The path to the checker contract (in BoC format).")
        .required()

    private val analyzedContracts by option("-c", "--contract")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .help("Contract to analyze (in BoC format).")
        .multiple(required = true)

    init {
        checkerContract =
            BocAnalyzer.loadContractFromBoc(checkerContractPath).also {
                it.isContractWithTSACheckerFunctions = true
            }
        contractsToAnalyze =
            analyzedContracts.map {
                BocAnalyzer.loadContractFromBoc(it)
            }
    }
}
