package org.ton.commands

import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.ton.options.FiftOptions
import org.usvm.machine.FiftAnalyzer
import java.nio.file.Path

class FiftAnalysis :
    ErrorsSarifDetector<Path>(name = "fift", help = "Options for analyzing smart contracts in Fift assembler") {
    private val fiftSourcesPath by option("-i", "--input")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .required()
        .help("The path to the Fift assembly of the smart contract")

    private val fiftOptions by FiftOptions()

    override fun run() {
        val analyzer =
            FiftAnalyzer(
                fiftStdlibPath = fiftOptions.fiftStdlibPath,
            )

        generateAndWriteSarifReport(
            analyzer = analyzer,
            sources = fiftSourcesPath,
        )
    }
}
