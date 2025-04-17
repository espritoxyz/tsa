package org.ton

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.transformValues
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import org.ton.bytecode.TsaContractCode
import org.ton.sarif.toSarifReport
import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.gen.generateTests
import org.ton.tlb.readFromJson
import org.usvm.machine.BocAnalyzer
import org.usvm.machine.FiftAnalyzer
import org.usvm.machine.FuncAnalyzer
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TactAnalyzer
import org.usvm.machine.TactSourcesDescription
import org.usvm.machine.TvmAnalyzer
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.machine.state.ContractId
import org.usvm.machine.toMethodId
import java.math.BigInteger
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ContractProperties : OptionGroup("Contract properties") {
    val contractData by option("-d", "--data").help("The serialized contract persistent data")
}

class SarifOptions : OptionGroup("SARIF options") {
    val sarifPath by option("-o", "--output")
        .path(mustExist = false, canBeFile = true, canBeDir = false)
        .help("The path to the output SARIF report file")
}

class FiftOptions : OptionGroup("Fift options") {
    val fiftStdlibPath by option("--fift-std")
        .path(mustExist = true, canBeDir = true, canBeFile = false)
        .required()
        .help("The path to the Fift standard library (dir containing Asm.fif, Fift.fif)")
}

class FuncOptions : OptionGroup("FunC options") {
    val funcStdlibPath by option("--func-std")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .required()
        .help("The path to the FunC standard library file (stdlib.fc)")
}

class TlbOptions : OptionGroup("TlB scheme options") {
    val tlbJsonPath by option("-t", "--tlb")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .help("The path to the parsed TL-B scheme.")

    companion object {
        fun extractInputInfo(path: Path?): Map<BigInteger, TvmInputInfo> {
            return if (path == null) {
                emptyMap()
            } else {
                val struct = readFromJson(path, "InternalMsgBody")
                    ?: error("Couldn't parse `InternalMsgBody` structure from $path")
                val info = TvmParameterInfo.SliceInfo(
                    TvmParameterInfo.DataCellInfo(
                        struct
                    )
                )
                mapOf(BigInteger.ZERO to TvmInputInfo(mapOf(0 to info)))
            }
        }
    }
}

class TestGeneration : CliktCommand(name = "test-gen", help = "Options for test generation for FunC projects") {
    private val projectPath by option("-p", "--project")
        .path(mustExist = true, canBeFile = false, canBeDir = true)
        .required()
        .help("The path to the FunC project")

    private val sourcesDescription: Pair<Path, TsRenderer.ContractType> by mutuallyExclusiveOptions(
        option("--boc")
            .help("Relative path from the project root to the BoC file")
            .path(canBeFile = true, canBeDir = false)
            .convert {
                require(!it.isAbsolute) {
                    "Contract file path must be relative (to project path)"
                }
                it to TsRenderer.ContractType.Boc
            },
        option("--func")
            .help("Relative path from the project root to the FunC file")
            .path(canBeFile = true, canBeDir = false)
            .convert {
                require(!it.isAbsolute) {
                    "Contract file path must be relative (to project path)"
                }
                it to TsRenderer.ContractType.Func
            },
    ).single().required()

    private val sourcesRelativePath by lazy { sourcesDescription.first }
    private val contractType by lazy { sourcesDescription.second }

    private val contractProperties by ContractProperties()

    // TODO: make these optional (only for FunC)
    private val fiftOptions by FiftOptions()
    private val funcOptions by FuncOptions()

    private val tlbOptions by TlbOptions()

    override fun run() {
        val analyzer = when (contractType) {
            TsRenderer.ContractType.Func -> {
                FuncAnalyzer(
                    funcStdlibPath = funcOptions.funcStdlibPath,
                    fiftStdlibPath = fiftOptions.fiftStdlibPath
                )
            }
            TsRenderer.ContractType.Boc -> {
                BocAnalyzer
            }
        }

        val absolutePath = projectPath.resolve(sourcesRelativePath)
        val inputInfo = TlbOptions.extractInputInfo(tlbOptions.tlbJsonPath)

        val results = analyzer.analyzeAllMethods(
            absolutePath,
            contractProperties.contractData,
            inputInfo = inputInfo,
        )

        generateTests(
            results,
            projectPath,
            sourcesRelativePath,
            contractType,
            useMinimization = true,
        )
    }
}

class TactAnalysis : ErrorsSarifDetector<TactSourcesDescription>(
    name = "tact",
    help = "Options for analyzing Tact sources of smart contracts"
) {
    private val tactConfigPath by option("-c", "--config")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .required()
        .help("The path to the Tact config (tact.config.json)")

    private val tactProjectName by option("-p", "--project")
        .required()
        .help("Name of the Tact project to analyze")

    private val tactContractName by option("-i", "--input")
        .required()
        .help("Name of the Tact smart contract to analyze")

    override fun run() {
        val sources = TactSourcesDescription(tactConfigPath, tactProjectName, tactContractName)

        generateAndWriteSarifReport(
            analyzer = TactAnalyzer,
            sources = sources,
        )
    }
}

class FuncAnalysis : ErrorsSarifDetector<Path>(name = "func", help = "Options for analyzing FunC sources of smart contracts") {
    private val funcSourcesPath by option("-i", "--input")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .required()
        .help("The path to the FunC source of the smart contract")

    private val fiftOptions by FiftOptions()
    private val funcOptions by FuncOptions()
    private val tlbOptions by TlbOptions()

    override fun run() {
        val analyzer = FuncAnalyzer(
            funcStdlibPath = funcOptions.funcStdlibPath,
            fiftStdlibPath = fiftOptions.fiftStdlibPath
        )

        generateAndWriteSarifReport(
            analyzer = analyzer,
            sources = funcSourcesPath,
            inputInfo = TlbOptions.extractInputInfo(tlbOptions.tlbJsonPath)
        )
    }
}

class FiftAnalysis : ErrorsSarifDetector<Path>(name = "fift", help = "Options for analyzing smart contracts in Fift assembler") {
    private val fiftSourcesPath by option("-i", "--input")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .required()
        .help("The path to the Fift assembly of the smart contract")

    private val fiftOptions by FiftOptions()

    override fun run() {
        val analyzer = FiftAnalyzer(
            fiftStdlibPath = fiftOptions.fiftStdlibPath
        )

        generateAndWriteSarifReport(
            analyzer = analyzer,
            sources = fiftSourcesPath,
        )
    }
}

class BocAnalysis : ErrorsSarifDetector<Path>(name = "boc", help = "Options for analyzing a smart contract in the BoC format") {
    private val bocPath by option("-i", "--input")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .required()
        .help("The path to the smart contract in the BoC format")

    override fun run() {
        val analyzer = BocAnalyzer

        generateAndWriteSarifReport(
            analyzer = analyzer,
            sources = bocPath,
        )
    }
}

class CheckerAnalysis : CliktCommand(
    name = "custom-checker",
    help = "Options for using custom checkers",
) {
    private val fiftOptions by FiftOptions()
    private val funcOptions by FuncOptions()

    private val tlbOptions by TlbOptions()

    private val checkerContractPath by option("--checker")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .help("The path to the checker contract.")
        .required()

    private val interContractSchemePath by option("-s", "--scheme")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .help("Scheme of the inter-contract communication.")

    private val pathOptionDescriptor = option().path(mustExist = true, canBeFile = true, canBeDir = false)
    private val typeOptionDescriptor = option().enum<ContractType>(ignoreCase = true)
    private val contractSources: List<ContractSources> by contractSourcesOption(pathOptionDescriptor, typeOptionDescriptor)
        .multiple(required = true)
        .validate {
            if (it.size > 1) {
                requireNotNull(interContractSchemePath) {
                    "Inter-contract communication scheme is required for multiple contracts"
                }
            }
        }

    private val fiftAnalyzer by lazy {
        FiftAnalyzer(
            fiftStdlibPath = fiftOptions.fiftStdlibPath
        )
    }

    private val funcAnalyzer by lazy {
        FuncAnalyzer(
            funcStdlibPath = funcOptions.funcStdlibPath,
            fiftStdlibPath = fiftOptions.fiftStdlibPath
        )
    }

    override fun run() {
        val checkerContract = getFuncContract(
            checkerContractPath,
            funcOptions.funcStdlibPath,
            fiftOptions.fiftStdlibPath,
            isTSAChecker = true
        )

        val contractsToAnalyze = contractSources.map { it.convertToTsaContractCode(fiftAnalyzer, funcAnalyzer) }

        // TODO support TL-B schemes in JAR
        val inputInfo = runCatching {
            TlbOptions.extractInputInfo(tlbOptions.tlbJsonPath).values.singleOrNull()
        }
            .getOrElse { TvmInputInfo() } // In case TL-B scheme is incorrect (not json format, for example), use empty scheme
            ?: TvmInputInfo() // In case TL-B scheme is not provided, use empty scheme

        val options = TvmOptions(
            intercontractOptions = IntercontractOptions(communicationScheme = interContractSchemePath?.extractIntercontractScheme()),
            turnOnTLBParsingChecks = false
        )

        val contracts = listOf(checkerContract) + contractsToAnalyze
        val result = analyzeInterContract(
            contracts,
            startContractId = 0, // Checker contract is the first to analyze
            methodId = TvmContext.RECEIVE_INTERNAL_ID,
            options = options,
            inputInfo = inputInfo,
        )

        echo(result.toSarifReport(methodsMapping = emptyMap(), useShortenedOutput = true))
    }
}

class InterContractAnalysis : CliktCommand(
    name = "inter-contract",
    help = "Options for analyzing inter-contract communication of smart contracts",
) {
    private val fiftOptions by FiftOptions()
    private val funcOptions by FuncOptions()

    private val interContractSchemePath by option("-s", "--scheme")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .required()
        .help("Scheme of the inter-contract communication.")

    private val fiftAnalyzer by lazy {
        FiftAnalyzer(
            fiftStdlibPath = fiftOptions.fiftStdlibPath
        )
    }

    private val funcAnalyzer by lazy {
        FuncAnalyzer(
            funcStdlibPath = funcOptions.funcStdlibPath,
            fiftStdlibPath = fiftOptions.fiftStdlibPath
        )
    }

    private val pathOptionDescriptor = option().path(mustExist = true, canBeFile = true, canBeDir = false)
    private val typeOptionDescriptor = option().enum<ContractType>(ignoreCase = true)

    private val contractSources: List<ContractSources> by contractSourcesOption(pathOptionDescriptor, typeOptionDescriptor)
        .multiple(required = true)

    private val startContractId: Int by option("-r", "--root")
        .int()
        .default(0)
        .help("Id of the root contract (numeration is by order of -c options).")

    private val methodId: Int by option("-m", "--method")
        .int()
        .default(0)
        .help("Id of the starting method in the root contract.")

    override fun run() {
        val contracts = contractSources.map { it.convertToTsaContractCode(fiftAnalyzer, funcAnalyzer) }

        val communicationScheme = interContractSchemePath.extractIntercontractScheme()
        val options = TvmOptions(intercontractOptions = IntercontractOptions(communicationScheme))

        val result = analyzeInterContract(
            contracts = contracts,
            startContractId = startContractId,
            methodId = methodId.toMethodId(),
            options = options,
        )

        echo(result.toSarifReport(methodsMapping = emptyMap(), useShortenedOutput = true))
    }
}

private fun Path.extractIntercontractScheme(): Map<ContractId, TvmContractHandlers> = communicationSchemeFromJson(readText())

private enum class ContractType {
    Tact,
    Func,
    Fift,
    Boc,
}

private sealed interface ContractSources {
    fun convertToTsaContractCode(fiftAnalyzer: FiftAnalyzer, funcAnalyzer: FuncAnalyzer): TsaContractCode
}
private data class SinglePath(val type: ContractType, val path: Path) : ContractSources {
    override fun convertToTsaContractCode(
        fiftAnalyzer: FiftAnalyzer,
        funcAnalyzer: FuncAnalyzer
    ): TsaContractCode {
        val analyzer = when (type) {
            ContractType.Boc -> BocAnalyzer
            ContractType.Func -> funcAnalyzer
            ContractType.Fift -> fiftAnalyzer
            ContractType.Tact -> error("Unexpected contract type $type with a single path $path")
        }

        return analyzer.convertToTvmContractCode(path)
    }
}
private data class TactPath(val tactPath: TactSourcesDescription) : ContractSources {
    override fun convertToTsaContractCode(
        fiftAnalyzer: FiftAnalyzer,
        funcAnalyzer: FuncAnalyzer
    ): TsaContractCode = TactAnalyzer.convertToTvmContractCode(tactPath)
}

private fun ParameterHolder.contractSourcesOption(
    pathOptionDescriptor: NullableOption<Path, Path>,
    typeOptionDescriptor: NullableOption<ContractType, ContractType>
) = option("-c", "--contract")
    .help(
        """
                Contract to analyze. Must be given in format <contract-type> <options>.
                
                <contract-type> can be Tact, Func, Fift or Boc.
                
                For Func, Fift and Boc <options> is path to contract sources.
                
                For Tact, <options> is three values separated by space:
                <path to tact.config.json> <project name> <contract name>
                
                This option should be used for each analyzed contract separately.
                
                Examples:
                
                -c func jetton-wallet.fc
                
                -c tact path/to/tact.config.json Jetton JettonWallet
            
            """.trimIndent()
    )
    .transformValues(nvalues = 2..4) { args ->
        val typeRaw = args[0]
        val type = typeOptionDescriptor.transformValue(this, typeRaw)
        val pathRaw = args[1]
        val path = pathOptionDescriptor.transformValue(this, pathRaw)
        if (type == ContractType.Tact) {
            require(args.size == 4) {
                "Tact expects 3 parameters: path to tact.config.json, project name, contract name."
            }
            val taskName = args[2]
            val contractName = args[3]
            TactPath(TactSourcesDescription(path, taskName, contractName))
        } else {
            require(args.size == 2) {
                "Func, Fift and Boc expect only 1 parameter: path to contract source."
            }
            SinglePath(type, path)
        }
    }

class TonAnalysis : NoOpCliktCommand()

sealed class ErrorsSarifDetector<SourcesDescription>(name: String, help: String) : CliktCommand(name = name, help = help) {
    private val contractProperties by ContractProperties()
    private val sarifOptions by SarifOptions()

    fun generateAndWriteSarifReport(
        analyzer: TvmAnalyzer<SourcesDescription>,
        sources: SourcesDescription,
        inputInfo: Map<BigInteger, TvmInputInfo> = emptyMap()
    ) {
        val analysisResult = analyzer.analyzeAllMethods(
            sources = sources,
            contractDataHex = contractProperties.contractData,
            inputInfo = inputInfo,
        )
        val sarifReport = analysisResult.toSarifReport(methodsMapping = emptyMap())

        sarifOptions.sarifPath?.writeText(sarifReport) ?: run {
            echo(sarifReport)
        }
    }
}

fun main(args: Array<String>) = TonAnalysis()
    .subcommands(
        TactAnalysis(),
        FuncAnalysis(),
        FiftAnalysis(),
        BocAnalysis(),
        TestGeneration(),
        CheckerAnalysis(),
        InterContractAnalysis()
    ).main(args)
