package org.ton.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.path
import org.ton.TvmInputInfo
import org.ton.boc.BagOfCells
import org.ton.bytecode.TsaContractCode
import org.ton.common.performAnalysisInterContract
import org.ton.options.AnalysisOptions
import org.ton.options.NullablePath
import org.ton.options.SarifOptions
import org.ton.options.TlbCLIOptions
import org.ton.sarif.toSarifReport
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import kotlin.io.path.writeText

sealed class AbstractCheckerAnalysis(
    commandName: String,
) : CliktCommand(
        name = commandName,
        help = "Options for using custom checkers",
    ) {
    abstract val checkerContract: TsaContractCode
    abstract val contractsToAnalyze: List<TsaContractCode>

    private val sarifOptions by SarifOptions()

    private val tlbOptions by TlbCLIOptions()

    protected val interContractSchemePath by option("-s", "--scheme")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .help("Scheme of the inter-contract communication.")

    protected val pathOptionDescriptor = option().path(mustExist = true, canBeFile = true, canBeDir = false)

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
            require(it.isEmpty() || it.size == contractsToAnalyze.size) {
                "If data specified, number of data paths should be equal to the number of contracts (excluding checker contract)"
            }
        }

    private val analysisOptions by AnalysisOptions()

    private val checkerConcreteDataPath by option("--checker-data")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .help("Path to .boc file with concrete data for checker contract.")

    override fun run() {
        check(checkerContract.isContractWithTSACheckerFunctions) {
            "Checker contract must be marked as checker"
        }

        // TODO support TL-B schemes in JAR
        val inputInfo =
            runCatching {
                TlbCLIOptions.extractInputInfo(tlbOptions.tlbJsonPath).values.singleOrNull()
            }.getOrElse {
                // In case TL-B scheme is incorrect (not json format, for example), use empty scheme
                TvmInputInfo()
            } ?: TvmInputInfo() // In case TL-B scheme is not provided, use empty scheme

        val checkerContractData =
            checkerConcreteDataPath?.let {
                val bytes = it.toFile().readBytes()
                val dataCell = BagOfCells(bytes).roots.single()
                TvmConcreteContractData(contractC4 = dataCell)
            } ?: TvmConcreteContractData()

        val concreteContractData =
            listOf(checkerContractData) +
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
