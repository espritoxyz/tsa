package org.ton.options

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path

class SarifOptions : OptionGroup("SARIF options") {
    val sarifPath by option("-o", "--output")
        .path(mustExist = false, canBeFile = true, canBeDir = false)
        .help("The path to the output SARIF report file")

    val excludeUserDefinedErrors by option("--no-user-errors")
        .flag()
        .help("Do not report executions with user-defined errors")
}
