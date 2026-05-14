package org.ton.common

import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmRealInst
import org.ton.options.AnalysisOptions
import org.usvm.machine.FollowTrace
import org.usvm.test.resolver.TvmContractSymbolicTestResult
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmSymbolicTestSuite
import org.usvm.test.resolver.exitCode
import kotlin.io.path.writeText

fun writeCoveredInstructions(
    analysisOptions: AnalysisOptions,
    result: TvmContractSymbolicTestResult,
) {
    val insts =
        result.flatMap { tests ->
            tests.flatMap {
                it.coveredInstructions
            }
        }

    writeCoveredInstructions(analysisOptions, insts)
}

fun writeCoveredInstructions(
    analysisOptions: AnalysisOptions,
    tests: TvmSymbolicTestSuite,
) {
    val insts =
        tests.flatMap {
            it.coveredInstructions
        }

    writeCoveredInstructions(analysisOptions, insts)
}

private fun writeCoveredInstructions(
    analysisOptions: AnalysisOptions,
    insts: List<TvmInst>,
) {
    val path =
        analysisOptions.coveredInstructionsListPath
            ?: return

    val lines =
        insts.mapNotNull { inst ->
            (inst as? TvmRealInst)?.physicalLocation?.let { loc ->
                "${loc.cellHashHex} ${loc.offset}"
            }
        }

    val text = lines.toSet().joinToString("\n")
    path.writeText(text)
}

fun writeTraceForExitCode(
    analysisOptions: AnalysisOptions,
    result: TvmContractSymbolicTestResult,
) {
    val tests = result.flatMap { it.tests }
    writeTraceForExitCode(analysisOptions, tests)
}

fun writeTraceForExitCode(
    analysisOptions: AnalysisOptions,
    result: TvmSymbolicTestSuite,
) {
    writeTraceForExitCode(analysisOptions, result.tests)
}

private fun writeTraceForExitCode(
    analysisOptions: AnalysisOptions,
    tests: List<TvmSymbolicTest>,
) {
    val path = analysisOptions.saveTracePath ?: return
    val exitCode =
        analysisOptions.saveTraceExitCode
            ?: error("--save-trace-path requires --save-trace-exit-code")

    val test =
        tests.firstOrNull { it.result.exitCode() == exitCode }
            ?: error("No test with exit code $exitCode found; nothing to save to $path")

    val locations =
        test.coveredInstructions.mapNotNull { inst ->
            (inst as? TvmRealInst)?.physicalLocation?.let { it.cellHashHex to it.offset }
        }

    FollowTrace.save(path.toFile(), locations)
}
