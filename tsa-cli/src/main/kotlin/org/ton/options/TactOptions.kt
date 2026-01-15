package org.ton.options

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import org.usvm.machine.TactAnalyzer

class TactOptions : OptionGroup("Tact options") {
    val tactExecutable by option("--tact")
        .default(TactAnalyzer.DEFAULT_TACT_EXECUTABLE)
        .help("Tact executable. Default: ${TactAnalyzer.DEFAULT_TACT_EXECUTABLE}")
}
