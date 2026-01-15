package org.ton.commands

import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.usvm.machine.BocAnalyzer
import java.nio.file.Path

class BocAnalysis :
    ErrorsSarifDetector<Path>(name = "boc", help = "Options for analyzing a smart contract in the BoC format") {
    private val bocPath by option("-i", "--input")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .required()
        .help("The path to the smart contract in the BoC format")

    override fun run() {
        val analyzer = BocAnalyzer

        generateAndWriteSarifReport(
            analyzer = analyzer,
            sources = bocPath,
        )
    }
}
