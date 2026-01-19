package org.ton.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.boolean
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
import org.usvm.test.resolver.TvmExecutionWithSoftFailure
import org.usvm.test.resolver.TvmSymbolicTestSuite
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestInput
import org.usvm.test.resolver.TvmTestSliceValue
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.writeText

/**
 * @param contractsC4 maps from contract ids
 * @param additionalInputs maps from additionalInput indices; the values are the cells the original msgBodies
 * pointed to that were cut by dataPos and refPos fields of the original slices
 */
private data class AdditionalOutput(
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
    protected val additionalInputsOutputDir by option("-a", "--additional-output")
        .path(mustExist = false)
        .help("Folder where to put additional test information (such as C4 of contracts the beginning of an execution)")

    protected val includeSoftFailure by option().boolean().default(true).help(
        "Include the executions that ended with Soft Failure",
    )

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
                TvmSymbolicTestSuite(
                    testSuite.methodId,
                    testSuite.methodCoverage,
                    testSuite.tests.filter {
                        includeSoftFailure || it.result !is TvmExecutionWithSoftFailure
                    },
                )
            }

        val sarifReport =
            result.toSarifReport(
                methodsMapping = emptyMap(),
                useShortenedOutput = false,
                excludeUserDefinedErrors = sarifOptions.excludeUserDefinedErrors,
            )

        sarifOptions.sarifPath?.writeText(sarifReport) ?: run {
            echo(sarifReport)
        }
        val outputDirPath = additionalInputsOutputDir
        if (outputDirPath != null) {
            val additionalInputs = extractedAdditionalInputsFromTest(result)
            val outputDirCreated = outputDirPath.toFile().mkdirs()
            if (!outputDirCreated) {
                echo("failed to create output directory")
            } else {
                dumpAdditionalInputs(additionalInputs, outputDirPath)
            }
        }
    }

    private fun dumpAdditionalInputs(
        additionalInputs: List<AdditionalOutput>,
        outputDirPath: Path,
    ) {
        for (singleExecutionAdditionalOutput in additionalInputs) {
            val executionFolder = outputDirPath / "input_${singleExecutionAdditionalOutput.index}"
            executionFolder.toFile().mkdir()
            for ((contractId, c4) in singleExecutionAdditionalOutput.contractsC4) {
                val contractOutputFolder = executionFolder / "c4_$contractId"
                contractOutputFolder.toFile().mkdir()
                c4.dumpCellToFolder(contractOutputFolder)
            }
            for ((inputId, msgBodyCell) in singleExecutionAdditionalOutput.additionalInputs) {
                val additionalInputsOutputFolder = outputDirPath / "msgBody_$inputId"
                additionalInputsOutputFolder.toFile().mkdir()
                msgBodyCell.dumpCellToFolder(additionalInputsOutputFolder)
            }
        }
    }

    private fun extractedAdditionalInputsFromTest(result: TvmSymbolicTestSuite): List<AdditionalOutput> {
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
                            if (testInput is TvmTestInput.RecvInternalInput && contractId != checkerContractId) {
                                contractId to testInput.msgBody.toStrippedCell().toCellAsFileContent()
                            } else {
                                null
                            }
                        }.toMap()
                AdditionalOutput(index, c4s, messageBodies)
            }
        return toOutput
    }
}

private fun TvmTestSliceValue.toStrippedCell(): TvmTestDataCellValue =
    TvmTestDataCellValue(
        data = cell.data.drop(dataPos),
        refs = cell.refs.drop(refPos),
        knownTypes = cell.knownTypes,
    )
