package org.ton.commands

import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.ton.options.FiftOptions
import org.usvm.machine.FuncAnalyzer
import java.nio.file.Path

class FuncAnalysis :
    ErrorsSarifDetector<Path>(name = "func", help = "Options for analyzing FunC sources of smart contracts") {
    private val funcSourcesPath by option("-i", "--input")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .required()
        .help("The path to the FunC source of the smart contract")

    private val fiftOptions by FiftOptions()

    override fun run() {
        val analyzer =
            FuncAnalyzer(
                fiftStdlibPath = fiftOptions.fiftStdlibPath,
            )

        generateAndWriteSarifReport(
            analyzer = analyzer,
            sources = funcSourcesPath,
        )
    }
}
