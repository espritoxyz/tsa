package org.ton.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import org.ton.TvmInputInfo
import org.ton.common.performAnalysisInterContract
import org.ton.options.AnalysisOptions
import org.ton.options.ContractSources
import org.ton.options.ContractType
import org.ton.options.FiftOptions
import org.ton.options.SarifOptions
import org.ton.options.TactOptions
import org.ton.options.contractSourcesOption
import org.ton.sarif.toSarifReport
import org.usvm.machine.FiftAnalyzer
import org.usvm.machine.FuncAnalyzer
import org.usvm.machine.TactAnalyzer
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.toMethodId
import kotlin.io.path.writeText

class InterContractAnalysis :
    CliktCommand(
        name = "inter-contract",
        help = "Options for analyzing inter-contract communication of smart contracts",
    ) {
    private val fiftOptions by FiftOptions()
    private val tactOptions by TactOptions()

    private val sarifOptions by SarifOptions()

    private val interContractSchemePath by option("-s", "--scheme")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .required()
        .help("Scheme of the inter-contract communication.")

    private val fiftAnalyzer by lazy {
        FiftAnalyzer(
            fiftStdlibPath = fiftOptions.fiftStdlibPath,
        )
    }

    private val funcAnalyzer by lazy {
        FuncAnalyzer(
            fiftStdlibPath = fiftOptions.fiftStdlibPath,
        )
    }

    private val tactAnalyzer by lazy {
        TactAnalyzer(
            tactExecutable = tactOptions.tactExecutable,
        )
    }

    private val pathOptionDescriptor = option().path(mustExist = true, canBeFile = true, canBeDir = false)
    private val typeOptionDescriptor = option().enum<ContractType>(ignoreCase = true)

    private val contractSources: List<ContractSources> by contractSourcesOption(
        pathOptionDescriptor,
        typeOptionDescriptor,
    ).multiple(required = true)

    private val startContractId: Int by option("-r", "--root")
        .int()
        .default(0)
        .help("Id of the root contract (numeration is by order of -c options).")

    private val methodId: Int by option("-m", "--method")
        .int()
        .default(0)
        .help("Id of the starting method in the root contract.")

    private val analysisOptions by AnalysisOptions()

    override fun run() {
        val contracts =
            contractSources.map {
                it.convertToTsaContractCode(fiftAnalyzer, funcAnalyzer, tactAnalyzer)
            }

        val result =
            performAnalysisInterContract(
                contracts,
                concreteContractData = contracts.map { TvmConcreteContractData() }, // TODO: support conrete data
                interContractSchemePath,
                startContractId,
                methodId = methodId.toMethodId(),
                inputInfo = TvmInputInfo(), // TODO: support TL-B
                analysisOptions = analysisOptions,
                turnOnTLBParsingChecks = false,
                useReceiverInput = true,
            )

        val sarifReport =
            result.toSarifReport(
                methodsMapping = emptyMap(),
                useShortenedOutput = false,
                excludeUserDefinedErrors = sarifOptions.excludeUserDefinedErrors,
            )

        sarifOptions.sarifPath?.writeText(sarifReport) ?: run {
            echo(sarifReport)
        }
    }
}
