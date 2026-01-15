package org.ton.options

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import org.usvm.machine.TvmMachine.Companion.DEFAULT_LOOP_ITERATIONS_LIMIT
import org.usvm.machine.TvmMachine.Companion.DEFAULT_MAX_RECURSION_DEPTH

class AnalysisOptions : OptionGroup("Symbolic analysis options") {
    val analyzeBouncedMessages by option("--analyze-bounced-messages")
        .flag()
        .help("Consider inputs when the message is bounced.")

    val timeout by option("--timeout")
        .int()
        .help("Analysis timeout in seconds.")

    val continueOnContractException by option("--continue-on-contract-exception")
        .flag()
        .help(
            "Do not stop analysis if an exception occurred in some analyzed contract. " +
                "If an exception occurred in checker contract, stop anyway.",
        )

    val maxRecursionDepth by option("--max-recursion-depth")
        .int()
        .default(DEFAULT_MAX_RECURSION_DEPTH)
        .help {
            "Skip executions where some method occurs in a call stack more times then the given limit. " +
                "Default: $DEFAULT_MAX_RECURSION_DEPTH."
        }

    val noRecursionDepthLimit by option("--no-recursion-depth-limit")
        .flag()
        .help("If set, ignore --max-recursion-depth.")

    val iterationLimit by option("--iteration-limit")
        .int()
        .default(DEFAULT_LOOP_ITERATIONS_LIMIT)
        .help {
            "Skip executions where the number of iterations in a loop exceeds the given limit. " +
                "The number of iterations is counted by how many times the loop body executes." +
                "Only the iterations where forking occurred are accounted for by this restriction." +
                "Default: $DEFAULT_LOOP_ITERATIONS_LIMIT."
        }

    val noIterationLimit by option("--no-iteration-limit")
        .flag()
        .help("If set, ignore --iteration-limit.")

    val stopWhenExitCodesFound by option("--stop-when-exit-codes-found")
        .int()
        .multiple()
        .help {
            "Stop the analysis right after executions with all required exit codes are found. " +
                "This option can be used several times for different exit codes"
        }

    val coveredInstructionsListPath by option("--covered-instructions-list")
        .path()
        .help("File to write covered TVM instructions (in hash+offset format).")
}
