package org.ton.commands

import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.ton.options.TactOptions
import org.usvm.machine.TactAnalyzer
import org.usvm.machine.TactSourcesDescription

class TactAnalysis :
    ErrorsSarifDetector<TactSourcesDescription>(
        name = "tact",
        help = "Options for analyzing Tact sources of smart contracts",
    ) {
    private val tactConfigPath by option("-c", "--config")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .required()
        .help("The path to the Tact config (tact.config.json)")

    private val tactProjectName by option("-p", "--project")
        .required()
        .help("Name of the Tact project to analyze")

    private val tactContractName by option("-i", "--input")
        .required()
        .help("Name of the Tact smart contract to analyze")

    private val tactOptions by TactOptions()

    override fun run() {
        val sources = TactSourcesDescription(tactConfigPath, tactProjectName, tactContractName)

        generateAndWriteSarifReport(
            analyzer = TactAnalyzer(tactOptions.tactExecutable),
            sources = sources,
        )
    }
}
