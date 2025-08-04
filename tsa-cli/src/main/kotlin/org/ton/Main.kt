package org.ton

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.help
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.OptionCallTransformContext
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.transformValues
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.ksmt.utils.uncheckedCast
import org.ton.boc.BagOfCells
import java.math.BigInteger
import java.nio.file.Path
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
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.machine.hexToCell
import org.usvm.machine.state.ContractId
import org.usvm.machine.toMethodId
import org.usvm.test.resolver.TvmContractSymbolicTestResult
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.seconds

class ContractProperties : OptionGroup("Contract properties") {
    val contractData by option("-d", "--data").help("The serialized contract persistent data")
}

private sealed interface AnalysisTarget
private data object AllMethods : AnalysisTarget
private data object Receivers : AnalysisTarget
private data class SpecificMethod(val methodId: Int) : AnalysisTarget

private fun CliktCommand.analysisTargetOption() = mutuallyExclusiveOptions(
    option("--method").int().help("Id of the method to analyze. If not specified, analyze all methods").convert { SpecificMethod(it) },
    option("--analyze-receivers").flag().help("Analyze recv_internal and recv_external").convert { Receivers },
    option("--analyze-all-methods").flag().help("Analyze all methods (applicable only for contracts with default main method)").convert { AllMethods }
)
    .single()
    .default(Receivers)
    .help("Analysis target", "What to analyze. By default, only receivers (recv_interval and recv_external) are analyzed.")

class SarifOptions : OptionGroup("SARIF options") {
    val sarifPath by option("-o", "--output")
        .path(mustExist = false, canBeFile = true, canBeDir = false)
        .help("The path to the output SARIF report file")

    val excludeUserDefinedErrors by option("--no-user-errors")
        .flag()
        .help("Do not report executions with user-defined errors")
}

class FiftOptions : OptionGroup("Fift options") {
    val fiftStdlibPath by option("--fift-std")
        .path(mustExist = true, canBeDir = true, canBeFile = false)
        .required()
        .help("The path to the Fift standard library (dir containing Asm.fif, Fift.fif)")
}

class TactOptions : OptionGroup("Tact options") {
    val tactExecutable by option("--tact")
        .default(TactAnalyzer.DEFAULT_TACT_EXECUTABLE)
        .help("Tact executable. Default: ${TactAnalyzer.DEFAULT_TACT_EXECUTABLE}")
}

class TlbCLIOptions : OptionGroup("TlB scheme options") {
    val tlbJsonPath by option("-t", "--tlb")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .help("The path to the parsed TL-B scheme.")

    val doNotPerformTlbChecks by option("--no-tlb-checks")
        .flag()
        .help("Turn off TL-B parsing checks")

    companion object {
        fun extractInputInfo(path: Path?): Map<BigInteger, TvmInputInfo> {
            return if (path == null) {
                emptyMap()
            } else {
                val struct = readFromJson(path, "InternalMsgBody") as? TlbCompositeLabel
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

class AnalysisOptions : OptionGroup("Symbolic analysis options") {
    val analyzeBouncedMessages by option("--analyze-bounced-messages")
        .flag()
        .help("Consider inputs when the message is bounced.")

    val timeout by option("--timeout")
        .int()
        .help("Analysis timeout in seconds.")
}

private fun <SourcesDescription> performAnalysis(
    analyzer: TvmAnalyzer<SourcesDescription>,
    sources: SourcesDescription,
    contractData: String?,
    target: AnalysisTarget,
    tlbOptions: TlbCLIOptions,
    analysisOptions: AnalysisOptions,
): TvmContractSymbolicTestResult {
    val options = TvmOptions(
        quietMode = true,
        turnOnTLBParsingChecks = !tlbOptions.doNotPerformTlbChecks,
        analyzeBouncedMessaged = analysisOptions.analyzeBouncedMessages,
        timeout = analysisOptions.timeout?.seconds ?: INFINITE,
    )
    val inputInfo = TlbCLIOptions.extractInputInfo(tlbOptions.tlbJsonPath)
    val methodIds: List<BigInteger>? = when (target) {
        is AllMethods -> null
        is SpecificMethod -> listOf(target.methodId.toMethodId())
        is Receivers -> listOf(TvmContext.RECEIVE_INTERNAL_ID, TvmContext.RECEIVE_EXTERNAL_ID)
    }

    val concreteData = TvmConcreteContractData(contractC4 = contractData?.hexToCell())

    return if (methodIds == null) {
        analyzer.analyzeAllMethods(
            sources,
            concreteContractData = concreteData,
            inputInfo = inputInfo,
            tvmOptions = options,
        )

    } else {
        val testSets = methodIds.map { methodId ->
            analyzer.analyzeSpecificMethod(
                sources,
                methodId,
                concreteContractData = concreteData,
                inputInfo = inputInfo[methodId] ?: TvmInputInfo(),
                tvmOptions = options,
            )
        }

        TvmContractSymbolicTestResult(testSets)
    }
}


class TestGeneration : CliktCommand(name = "test-gen", help = "Options for test generation for FunC projects") {
    private val projectPath by option("-p", "--project")
        .path(mustExist = true, canBeFile = false, canBeDir = true)
        .required()
        .help("The path to the sandbox project")

    private val pathOptionDescriptor = option().path(canBeFile = true, canBeDir = false)
    private val typeOptionDescriptor = option().enum<ContractType>(ignoreCase = true)
    private val contractSources: ContractSources by contractSourcesOption(pathOptionDescriptor, typeOptionDescriptor) {
        require(!it.isAbsolute) {
            "File path must be relative (to project path)"
        }
    }.required()

    private val contractType by lazy {
        when (val optionSources = contractSources) {
            is SinglePath -> optionSources.type
            is TactPath -> ContractType.Tact
        }
    }

    private val contractProperties by ContractProperties()

    // TODO: make these optional (only for FunC)
    private val fiftOptions by FiftOptions()
    private val tactOptions by TactOptions()

    private val tlbOptions by TlbCLIOptions()

    private val analysisOptions by AnalysisOptions()

    private fun toAbsolutePath(relativePath: Path) = projectPath.resolve(relativePath).normalize()

    private val target by analysisTargetOption()

    override fun run() {
        val tactAnalyzer = TactAnalyzer(tactOptions.tactExecutable)

        val (sourcesAbsolutePath, sourcesRelativePath) = when (val optionSources = contractSources) {
            is SinglePath -> optionSources.path.let { toAbsolutePath(it) to it }

            is TactPath -> {
                val configAbsolutePath = toAbsolutePath(optionSources.tactPath.configPath)
                val sourcesAbsolutePath = optionSources.tactPath.copy(configPath = configAbsolutePath)
                val bocAbsolutePath = tactAnalyzer.getBocAbsolutePath(sourcesAbsolutePath)
                val bocRelativePath = bocAbsolutePath.relativeTo(projectPath)

                sourcesAbsolutePath to bocRelativePath
            }
        }
        val analyzer = when (contractType) {
            ContractType.Func -> FuncAnalyzer(fiftOptions.fiftStdlibPath)
            ContractType.Boc -> BocAnalyzer
            ContractType.Tact -> tactAnalyzer

            ContractType.Fift -> error("Fift is not supported")
        }

        val results = performAnalysis(
            analyzer.uncheckedCast(),
            sourcesAbsolutePath,
            contractProperties.contractData,
            target,
            tlbOptions,
            analysisOptions,
        )

        val testGenContractType = when (contractType) {
            ContractType.Func -> TsRenderer.ContractType.Func
            else -> TsRenderer.ContractType.Boc
        }

        generateTests(
            results,
            projectPath,
            sourcesRelativePath,
            testGenContractType,
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

    private val tactOptions by TactOptions()

    override fun run() {
        val sources = TactSourcesDescription(tactConfigPath, tactProjectName, tactContractName)

        generateAndWriteSarifReport(
            analyzer = TactAnalyzer(tactOptions.tactExecutable),
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

    override fun run() {
        val analyzer = FuncAnalyzer(
            fiftStdlibPath = fiftOptions.fiftStdlibPath
        )

        generateAndWriteSarifReport(
            analyzer = analyzer,
            sources = funcSourcesPath,
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
    private val contractSources: List<ContractSources> by contractSourcesOption(pathOptionDescriptor, typeOptionDescriptor)
        .multiple(required = true)
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
        }
        .convert { value ->
            if (value == "-") {
                NullablePath(null)
            } else {
                NullablePath(pathOptionDescriptor.transformValue(this, value))
            }
        }
        .multiple()
        .validate {
            require(it.isEmpty() || it.size == contractSources.size) {
                "If data specified, number of data paths should be equal to the number of contracts (excluding checker contract)"
            }
        }

    private val fiftAnalyzer by lazy {
        FiftAnalyzer(
            fiftStdlibPath = fiftOptions.fiftStdlibPath
        )
    }

    private val funcAnalyzer by lazy {
        FuncAnalyzer(
            fiftStdlibPath = fiftOptions.fiftStdlibPath
        )
    }

    private val tactAnalyzer by lazy {
        TactAnalyzer(
            tactExecutable = tactOptions.tactExecutable,
        )
    }

    override fun run() {
        val checkerContract = getFuncContract(
            checkerContractPath,
            fiftOptions.fiftStdlibPath,
            isTSAChecker = true
        )

        val contractsToAnalyze = contractSources.map {
            it.convertToTsaContractCode(fiftAnalyzer, funcAnalyzer, tactAnalyzer)
        }

        // TODO support TL-B schemes in JAR
        val inputInfo = runCatching {
            TlbCLIOptions.extractInputInfo(tlbOptions.tlbJsonPath).values.singleOrNull()
        }
            .getOrElse { TvmInputInfo() } // In case TL-B scheme is incorrect (not json format, for example), use empty scheme
            ?: TvmInputInfo() // In case TL-B scheme is not provided, use empty scheme

        val options = if (interContractSchemePath != null) {
            TvmOptions(
                intercontractOptions = IntercontractOptions(communicationScheme = interContractSchemePath?.extractIntercontractScheme()),
                turnOnTLBParsingChecks = false,
                enableOutMessageAnalysis = true,
            )
        } else {
            TvmOptions(turnOnTLBParsingChecks = false)
        }

        val concreteContractData = listOf(TvmConcreteContractData()) + concreteData.map { path ->
            path.path ?: return@map TvmConcreteContractData()
            val bytes = path.path.toFile().readBytes()
            val dataCell = BagOfCells(bytes).roots.single()
            TvmConcreteContractData(contractC4 = dataCell)
        }.ifEmpty {
            contractsToAnalyze.map { TvmConcreteContractData() }
        }

        val contracts = listOf(checkerContract) + contractsToAnalyze
        val result = analyzeInterContract(
            contracts,
            startContractId = 0, // Checker contract is the first to analyze
            methodId = TvmContext.RECEIVE_INTERNAL_ID,
            options = options,
            inputInfo = inputInfo,
            concreteContractData = concreteContractData,
        )

        val sarifReport = result.toSarifReport(
            methodsMapping = emptyMap(),
            useShortenedOutput = false,
            excludeUserDefinedErrors = sarifOptions.excludeUserDefinedErrors,
        )

        sarifOptions.sarifPath?.writeText(sarifReport) ?: run {
            echo(sarifReport)
        }
    }
}

@JvmInline
private value class NullablePath(
    val path: Path?
)

class InterContractAnalysis : CliktCommand(
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
            fiftStdlibPath = fiftOptions.fiftStdlibPath
        )
    }

    private val funcAnalyzer by lazy {
        FuncAnalyzer(
            fiftStdlibPath = fiftOptions.fiftStdlibPath
        )
    }

    private val tactAnalyzer by lazy {
        TactAnalyzer(
            tactExecutable = tactOptions.tactExecutable
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
        val contracts = contractSources.map {
            it.convertToTsaContractCode(fiftAnalyzer, funcAnalyzer, tactAnalyzer)
        }

        val communicationScheme = interContractSchemePath.extractIntercontractScheme()
        val options = TvmOptions(intercontractOptions = IntercontractOptions(communicationScheme))

        val result = analyzeInterContract(
            contracts = contracts,
            startContractId = startContractId,
            methodId = methodId.toMethodId(),
            options = options,
        )

        val sarifReport = result.toSarifReport(
            methodsMapping = emptyMap(),
            useShortenedOutput = false,
            excludeUserDefinedErrors = sarifOptions.excludeUserDefinedErrors,
        )

        sarifOptions.sarifPath?.writeText(sarifReport) ?: run {
            echo(sarifReport)
        }
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
    fun convertToTsaContractCode(fiftAnalyzer: FiftAnalyzer, funcAnalyzer: FuncAnalyzer, tactAnalyzer: TactAnalyzer): TsaContractCode
}
private data class SinglePath(val type: ContractType, val path: Path) : ContractSources {
    override fun convertToTsaContractCode(
        fiftAnalyzer: FiftAnalyzer,
        funcAnalyzer: FuncAnalyzer,
        tactAnalyzer: TactAnalyzer,
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
        funcAnalyzer: FuncAnalyzer,
        tactAnalyzer: TactAnalyzer,
    ): TsaContractCode = tactAnalyzer.convertToTvmContractCode(tactPath)
}

private fun ParameterHolder.contractSourcesOption(
    pathOptionDescriptor: NullableOption<Path, Path>,
    typeOptionDescriptor: NullableOption<ContractType, ContractType>,
    pathValidator: OptionCallTransformContext.(Path) -> Unit = { },
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
        val path = pathOptionDescriptor.transformValue(this, pathRaw).also { pathValidator(it) }
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

    private val tlbOptions by TlbCLIOptions()
    private val analysisOptions by AnalysisOptions()
    private val target by analysisTargetOption()

    fun generateAndWriteSarifReport(
        analyzer: TvmAnalyzer<SourcesDescription>,
        sources: SourcesDescription,
    ) {
        val analysisResult = performAnalysis(
            analyzer = analyzer,
            sources = sources,
            contractData = contractProperties.contractData,
            target = target,
            tlbOptions = tlbOptions,
            analysisOptions,
        )
        val sarifReport = analysisResult.toSarifReport(
            methodsMapping = emptyMap(),
            excludeUserDefinedErrors = sarifOptions.excludeUserDefinedErrors,
        )

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
