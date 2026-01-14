package org.ton.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import org.ton.TvmInputInfo
import org.ton.boc.BagOfCells
import org.ton.common.performAnalysisInterContract
import org.ton.options.AnalysisOptions
import org.ton.options.ContractSources
import org.ton.options.ContractType
import org.ton.options.FiftOptions
import org.ton.options.NullablePath
import org.ton.options.SarifOptions
import org.ton.options.TactOptions
import org.ton.options.TlbCLIOptions
import org.ton.options.contractSourcesOption
import org.ton.sarif.toSarifReport
import org.usvm.machine.FiftAnalyzer
import org.usvm.machine.FuncAnalyzer
import org.usvm.machine.TactAnalyzer
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.getFuncContract
import kotlin.io.path.writeText

class CheckerAnalysis :
    CliktCommand(
        name = "custom-checker",
        help = "Options for using custom checkers",
    ) {
    private val fiftOptions by FiftOptions()
    private val tactOptions by TactOptions()

    private val sarifOptions by SarifOptions()

    private val tlbOptions by TlbCLIOptions()

    private val checkerContractPath by option("--checker")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .help("The path to the checker contract.")
        .required()

    private val interContractSchemePath by option("-s", "--scheme")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .help("Scheme of the inter-contract communication.")

    private val pathOptionDescriptor = option().path(mustExist = true, canBeFile = true, canBeDir = false)
    private val typeOptionDescriptor = option().enum<ContractType>(ignoreCase = true)
    private val contractSources: List<ContractSources> by contractSourcesOption(
        pathOptionDescriptor,
        typeOptionDescriptor,
    ).multiple(required = true)
        .validate {
            if (it.size > 1) {
                requireNotNull(interContractSchemePath) {
                    "Inter-contract communication scheme is required for multiple contracts"
                }
            }
        }

    private val concreteData: List<NullablePath> by option("-d", "--data")
        .help {
            """
            Paths to .boc files with contract data.
            
            The order corresponds to the order of contracts with -c option.
            
            If data for a contract should be skipped, type "-".
            
            Example:
            
            -d data1.boc -d - -c Boc contract1.boc -c Func contract2.fc
            
            """.trimIndent()
        }.convert { value ->
            if (value == "-") {
                NullablePath(null)
            } else {
                NullablePath(pathOptionDescriptor.transformValue(this, value))
            }
        }.multiple()
        .validate {
            require(it.isEmpty() || it.size == contractSources.size) {
                "If data specified, number of data paths should be equal to the number of contracts (excluding checker contract)"
            }
        }

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

    private val analysisOptions by AnalysisOptions()

    override fun run() {
        val checkerContract =
            getFuncContract(
                checkerContractPath,
                fiftOptions.fiftStdlibPath,
                isTSAChecker = true,
            )

        val contractsToAnalyze =
            contractSources.map {
                it.convertToTsaContractCode(fiftAnalyzer, funcAnalyzer, tactAnalyzer)
            }

        // TODO support TL-B schemes in JAR
        val inputInfo =
            runCatching {
                TlbCLIOptions.extractInputInfo(tlbOptions.tlbJsonPath).values.singleOrNull()
            }.getOrElse {
                // In case TL-B scheme is incorrect (not json format, for example), use empty scheme
                TvmInputInfo()
            } ?: TvmInputInfo() // In case TL-B scheme is not provided, use empty scheme

        val concreteContractData =
            listOf(TvmConcreteContractData()) +
                concreteData
                    .map { path ->
                        path.path ?: return@map TvmConcreteContractData()
                        val bytes = path.path.toFile().readBytes()
                        val dataCell = BagOfCells(bytes).roots.single()
                        TvmConcreteContractData(contractC4 = dataCell)
                    }.ifEmpty {
                        contractsToAnalyze.map { TvmConcreteContractData() }
                    }

        val contracts = listOf(checkerContract) + contractsToAnalyze
        val result =
            performAnalysisInterContract(
                contracts,
                concreteContractData,
                interContractSchemePath,
                startContractId = 0, // Checker contract is the first to analyze
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                inputInfo = inputInfo,
                analysisOptions = analysisOptions,
                turnOnTLBParsingChecks = false,
                useReceiverInput = false,
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
