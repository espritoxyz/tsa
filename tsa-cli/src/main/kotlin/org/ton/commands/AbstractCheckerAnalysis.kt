package org.ton.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.path
import org.ton.CellAsFileContent
import org.ton.TvmInputInfo
import org.ton.boc.BagOfCells
import org.ton.bytecode.TsaContractCode
import org.ton.common.performAnalysisInterContract
import org.ton.dumpCellToFolder
import org.ton.options.AnalysisOptions
import org.ton.options.NullablePath
import org.ton.options.SarifOptions
import org.ton.options.TlbCLIOptions
import org.ton.sarif.toSarifReport
import org.ton.toCellAsFileContent
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.state.TvmUserDefinedFailure
import org.usvm.test.resolver.TvmExecutionWithSoftFailure
import org.usvm.test.resolver.TvmSymbolicTestSuite
import org.usvm.test.resolver.TvmTestFailure
import org.usvm.test.resolver.TvmTestInput
import org.usvm.test.resolver.truncateSliceCell
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.writeText

/**
 * @param contractsC4 maps from contract ids
 * @param additionalInputs maps from additionalInput indices; the values are the cells the original msgBodies
 * pointed to that were cut by dataPos and refPos fields of the original slices
 */
private data class ExportedInputs(
    val index: Int,
    val contractsC4: Map<Int, CellAsFileContent>,
    val additionalInputs: Map<Int, CellAsFileContent>,
)

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
    protected val exportedInputs by option("-a", "--exported-inputs")
        .path(mustExist = false, canBeDir = true, canBeFile = false)
        .help("Folder where to put additional test information (such as C4 of contracts the beginning of an execution)")

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
            ).let { testSuite ->
                // we want to filter here so the data is consistent between SARIF and other output artifacts
                TvmSymbolicTestSuite(
                    testSuite.methodId,
                    testSuite.methodCoverage,
                    testSuite.tests.filter {
                        val result = it.result
                        val softFailurePasses =
                            !sarifOptions.excludeUserDefinedErrors || result !is TvmExecutionWithSoftFailure
                        val userDefinedErrorPasses =
                            !sarifOptions.excludeSoftFailures ||
                                result !is TvmTestFailure ||
                                result.failure.exit is TvmUserDefinedFailure
                        softFailurePasses && userDefinedErrorPasses
                    },
                )
            }

        val sarifReport =
            result.toSarifReport(
                methodsMapping = emptyMap(),
                useShortenedOutput = false,
                excludeUserDefinedErrors = sarifOptions.excludeUserDefinedErrors,
            )

        val outputDirPath = exportedInputs
        if (outputDirPath != null) {
            val additionalInputs = extractedAdditionalInputsFromTest(result)
            if (outputDirPath.exists() && outputDirPath.isDirectory()) {
                outputDirPath.toFile().deleteRecursively()
            }
            val outputDirCreated = outputDirPath.toFile().mkdirs()
            if (!outputDirCreated) {
                echo("failed to create output directory")
            } else {
                dumpAdditionalInputs(additionalInputs, outputDirPath)
            }
        }
        // we might want to write SARIF into the exported-inputs folder, so we
        // only write SARIF after we've cleaned up the folder and filled it with some content
        sarifOptions.sarifPath?.writeText(sarifReport) ?: run {
            echo(sarifReport)
        }
    }

    private fun dumpAdditionalInputs(
        additionalInputs: List<ExportedInputs>,
        outputDirPath: Path,
    ) {
        for ((id, singleExecutionAdditionalOutput) in additionalInputs.withIndex()) {
            val executionOutputPath = outputDirPath / "execution_$id"
            val executionFolder = executionOutputPath / "input_${singleExecutionAdditionalOutput.index}"
            executionFolder.toFile().mkdirs()
            for ((contractId, c4) in singleExecutionAdditionalOutput.contractsC4) {
                val contractOutputFolder = executionFolder / "c4_$contractId"
                contractOutputFolder.toFile().mkdir()
                c4.dumpCellToFolder(contractOutputFolder)
            }
            for ((inputId, msgBodyCell) in singleExecutionAdditionalOutput.additionalInputs) {
                val additionalInputsOutputFolder = executionOutputPath / "msgBody_$inputId"
                additionalInputsOutputFolder.toFile().mkdir()
                msgBodyCell.dumpCellToFolder(additionalInputsOutputFolder)
            }
        }
    }

    private fun extractedAdditionalInputsFromTest(result: TvmSymbolicTestSuite): List<ExportedInputs> {
        val toOutput =
            result.tests.mapIndexed { index, test ->
                val checkerContractId = 0
                val c4s =
                    test.contractStatesBefore
                        .mapValues { it.value.data.toCellAsFileContent() }
                        .filter { it.key != checkerContractId }
                val messageBodies =
                    test.additionalInputs
                        .toList()
                        .mapNotNull { (contractId, testInput) ->
                            if (testInput is TvmTestInput.RecvInternalInput) {
                                contractId to truncateSliceCell(testInput.msgBody).toCellAsFileContent()
                            } else {
                                null
                            }
                        }.toMap()
                ExportedInputs(index, c4s, messageBodies)
            }
        return toOutput
    }
}
