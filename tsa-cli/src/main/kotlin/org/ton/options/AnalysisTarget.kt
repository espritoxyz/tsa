package org.ton.options

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.help
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import java.nio.file.Path

sealed interface AnalysisTarget

data object AllMethods : AnalysisTarget

data object Receivers : AnalysisTarget

data class SpecificMethod(
    val methodId: Int,
) : AnalysisTarget

fun CliktCommand.analysisTargetOption() =
    mutuallyExclusiveOptions(
        option("--method").int().help("Id of the method to analyze").convert { SpecificMethod(it) },
        option(
            "--analyze-receivers",
        ).flag().help("Analyze recv_internal and recv_external (default)").convert { Receivers },
        option(
            "--analyze-all-methods",
        ).flag().help("Analyze all methods (applicable only for contracts with default main method)").convert {
            AllMethods
        },
    ).single()
        .default(Receivers)
        .help(
            "Analysis target",
            "What to analyze. By default, only receivers (recv_interval and recv_external) are analyzed.",
        )

@JvmInline
value class NullablePath(
    val path: Path?,
)
