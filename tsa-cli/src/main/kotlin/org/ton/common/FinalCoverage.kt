package org.ton.common

import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmRealInst
import org.ton.options.AnalysisOptions
import org.usvm.test.resolver.TvmContractSymbolicTestResult
import org.usvm.test.resolver.TvmSymbolicTestSuite
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
