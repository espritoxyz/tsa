package org.ton.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import org.ton.common.performAnalysis
import org.ton.options.AnalysisOptions
import org.ton.options.ContractProperties
import org.ton.options.SarifOptions
import org.ton.options.TlbCLIOptions
import org.ton.options.analysisTargetOption
import org.ton.sarif.toSarifReport
import org.usvm.machine.TvmAnalyzer
import kotlin.io.path.writeText

sealed class ErrorsSarifDetector<SourcesDescription>(
    name: String,
    help: String,
) : CliktCommand(name = name, help = help) {
    private val contractProperties by ContractProperties()
    private val sarifOptions by SarifOptions()

    private val tlbOptions by TlbCLIOptions()
    private val analysisOptions by AnalysisOptions()
    private val target by analysisTargetOption()

    fun generateAndWriteSarifReport(
        analyzer: TvmAnalyzer<SourcesDescription>,
        sources: SourcesDescription,
    ) {
        val analysisResult =
            performAnalysis(
                analyzer = analyzer,
                sources = sources,
                contractData = contractProperties.contractData,
                target = target,
                tlbOptions = tlbOptions,
                analysisOptions,
            )
        val sarifReport =
            analysisResult.toSarifReport(
                methodsMapping = emptyMap(),
                excludeUserDefinedErrors = sarifOptions.excludeUserDefinedErrors,
            )

        sarifOptions.sarifPath?.writeText(sarifReport) ?: run {
            echo(sarifReport)
        }
    }
}
