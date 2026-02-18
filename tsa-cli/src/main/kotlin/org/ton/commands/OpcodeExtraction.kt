package org.ton.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import org.usvm.machine.BocAnalyzer
import org.usvm.machine.DEFAULT_OPCODE_EXTRACTION_TIMEOUT_SECONDS
import org.usvm.machine.TvmOpcodeExtractor
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

class OpcodeExtraction :
    CliktCommand(
        name = "opcodes",
        help = "Extract opcodes from smart contract in BoC format",
    ) {
    private val bocPath by option("-i", "--input")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .required()
        .help("Path to the smart contract in the BoC format")

    private val outputPath by option("-o", "--output")
        .path(mustExist = false, canBeFile = true, canBeDir = false)
        .required()
        .help("Path to a file with the found opcodes.")

    private val timeout by option("-t", "--timeout")
        .int()
        .default(DEFAULT_OPCODE_EXTRACTION_TIMEOUT_SECONDS)
        .help("Timeout for BFS symbolic analysis in seconds.")

    override fun run() {
        val extractor = TvmOpcodeExtractor()
        val code = BocAnalyzer.loadContractFromBoc(bocPath)
        val opcodes = extractor.extractOpcodes(code, timeout = timeout.seconds)
        val result = opcodes.joinToString(separator = "\n")
        outputPath.writeText(result)
    }
}
