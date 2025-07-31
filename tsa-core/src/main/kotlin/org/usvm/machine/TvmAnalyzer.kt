package org.usvm.machine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KLogging
import org.ton.TvmInputInfo
import org.ton.bytecode.MethodId
import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.setTSACheckerFunctions
import org.usvm.machine.FuncAnalyzer.Companion.FIFT_EXECUTABLE
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmState
import org.usvm.statistics.UMachineObserver
import org.usvm.stopstrategies.StopStrategy
import org.usvm.test.resolver.TvmContractSymbolicTestResult
import org.usvm.test.resolver.TvmMethodCoverage
import org.usvm.test.resolver.TvmSymbolicTestSuite
import org.usvm.test.resolver.TvmTestResolver
import org.usvm.utils.executeCommandWithTimeout
import org.usvm.utils.toText
import java.math.BigInteger
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

sealed interface TvmAnalyzer<SourcesDescription> {
    fun analyzeAllMethods(
        sources: SourcesDescription,
        concreteGeneralData: TvmConcreteGeneralData = TvmConcreteGeneralData(),
        concreteContractData: TvmConcreteContractData = TvmConcreteContractData(),
        methodsBlackList: Set<MethodId> = hashSetOf(),
        methodsWhiteList: Set<MethodId>? = null,
        inputInfo: Map<BigInteger, TvmInputInfo> = emptyMap(),
        tvmOptions: TvmOptions = TvmOptions(quietMode = true, timeout = 10.minutes),
        takeEmptyTests: Boolean = false,
    ): TvmContractSymbolicTestResult {
        val contract = convertToTvmContractCode(sources)
        return analyzeAllMethods(contract, methodsBlackList, methodsWhiteList, concreteGeneralData, concreteContractData, inputInfo, tvmOptions)
    }

    fun analyzeSpecificMethod(
        sources: SourcesDescription,
        methodId: MethodId,
        concreteGeneralData: TvmConcreteGeneralData = TvmConcreteGeneralData(),
        concreteContractData: TvmConcreteContractData = TvmConcreteContractData(),
        inputInfo: TvmInputInfo = TvmInputInfo(),
        tvmOptions: TvmOptions = TvmOptions(quietMode = true, timeout = 10.minutes),
    ): TvmSymbolicTestSuite {
        val contract = convertToTvmContractCode(sources)
        return analyzeSpecificMethod(contract, methodId, concreteGeneralData, concreteContractData, inputInfo, tvmOptions)
    }

    fun convertToTvmContractCode(sources: SourcesDescription): TsaContractCode
}

data class TactSourcesDescription(
    val configPath: Path,
    val projectName: String,
    val contractName: String,
)

class TactAnalyzer(
    private val tactExecutable: String = DEFAULT_TACT_EXECUTABLE
) : TvmAnalyzer<TactSourcesDescription> {
    override fun convertToTvmContractCode(sources: TactSourcesDescription): TsaContractCode {
        compileTact(sources.configPath)

        return TsaContractCode.construct(getBocAbsolutePath(sources))
    }

    fun getBocAbsolutePath(sources: TactSourcesDescription): Path {
        val config = readTactConfig(sources.configPath)
        val project = config.projects.firstOrNull {
            it.name == sources.projectName
        } ?: error("Project with name ${sources.projectName} not found.")
        val curDirectory = sources.configPath.parent?.toAbsolutePath() ?: Paths.get("").toAbsolutePath()
        val outputDir = curDirectory / project.output
        val bocFileName = "${sources.projectName}_${sources.contractName}.code.boc"

        return outputDir.resolve(bocFileName).normalize()
    }

    private fun readTactConfig(configPath: Path): TactConfig {
        val fileContent = configPath.toFile().readText()
        val configJson = Json {
            ignoreUnknownKeys = true
        }
        return configJson.decodeFromString(fileContent)
    }

    private fun compileTact(configFile: Path) {
        val tactCommand = "$tactExecutable --config ${configFile.absolutePathString()}"
        val executionCommand = tactCommand.toExecutionCommand()
        val (exitValue, completedInTime, output, errors) = executeCommandWithTimeout(
            executionCommand,
            COMPILER_TIMEOUT,
            processWorkingDirectory = configFile.parent?.toFile() ?: Paths.get("").toAbsolutePath().toFile(),
        )

        check(completedInTime) {
            "Tact compilation process has not finished in $COMPILER_TIMEOUT seconds"
        }

        check(exitValue == 0 && errors.isEmpty()) {
            "Tact compilation failed with an error, exit code $exitValue, errors: \n${errors.toText()}\n, output:\n${output.toText()}"
        }
    }

    @Serializable
    private data class TactConfig(val projects: List<TactProject>)

    @Serializable
    private data class TactProject(
        val name: String,
        val path: String,
        val output: String,
    )

    companion object {
        const val DEFAULT_TACT_EXECUTABLE: String = "tact"
    }
}

class FuncAnalyzer(
    private val funcStdlibPath: Path,
    private val fiftStdlibPath: Path,
) : TvmAnalyzer<Path> {
    private val funcExecutablePath: Path = Paths.get(FUNC_EXECUTABLE)
    private val fiftExecutablePath: Path = Paths.get(FIFT_EXECUTABLE)

    override fun convertToTvmContractCode(sources: Path): TsaContractCode {
        val tmpBocFile = createTempFile(suffix = ".boc")
        try {
            compileFuncSourceToBoc(sources, tmpBocFile)
            return BocAnalyzer.loadContractFromBoc(tmpBocFile)
        } finally {
            tmpBocFile.deleteIfExists()
        }
    }

    fun compileFuncSourceToFift(funcSourcesPath: Path, fiftFilePath: Path) {
        val funcCommand = "$funcExecutablePath -AP $funcStdlibPath ${funcSourcesPath.absolutePathString()}"
        val executionCommand = funcCommand.toExecutionCommand()
        val (exitValue, completedInTime, output, errors) = executeCommandWithTimeout(executionCommand, COMPILER_TIMEOUT)

        check(completedInTime) {
            "FunC compilation to Fift has not finished in $COMPILER_TIMEOUT seconds"
        }

        check(exitValue == 0) {
            "FunC compilation failed with an error, exit code $exitValue, errors: \n${errors.toText()}"
        }
        val fiftIncludePreamble = """"Fift.fif" include"""
        val fiftCode = "$fiftIncludePreamble\n${output.toText()}"

        fiftFilePath.writeText(fiftCode)
    }

    fun compileFuncSourceToBoc(funcSourcesPath: Path, bocFilePath: Path) {
        val funcCommand =
            "$funcExecutablePath -W ${bocFilePath.absolutePathString()} $funcStdlibPath ${funcSourcesPath.absolutePathString()}"
        val fiftCommand = "$fiftExecutablePath -I $fiftStdlibPath"
        val command = "$funcCommand | $fiftCommand"
        val executionCommand = command.toExecutionCommand()
        val (exitValue, completedInTime, _, errors) = executeCommandWithTimeout(executionCommand, COMPILER_TIMEOUT)

        check(completedInTime) {
            "FunC compilation to BoC has not finished in $COMPILER_TIMEOUT seconds"
        }

        // Ensure the BoC file is not empty after compilation
        val isBocEmptyOrMissing = bocFilePath.toFile().length() == 0L

        // we don't check that [errors] is empty, because there is a case, when it reports something strange,
        // but compilation is still successful.
        // Example of such message (not present in older version of Fift):
        // [ 1][t 0][2025-03-21 09:37:26.441386890][Fift.cpp:66]   top: <continuation 0x55871d44a300>
        // level 1: <continuation 0x55871d44a340>
        // level 2: <text interpreter continuation>
        // [ 1][t 0][2025-03-21 09:37:26.441586888][Fift.cpp:69]   Error::-?
        check(exitValue == 0 && bocFilePath.exists() && !isBocEmptyOrMissing) {
            """
                FunC compilation to BoC failed with an error, exit code $exitValue, errors: 
                ${errors.toText()}
                Command:
                $command
            """.trimIndent()
        }
    }

    companion object {
        const val FUNC_EXECUTABLE = "func"
        const val FIFT_EXECUTABLE = "fift"
    }
}

class FiftAnalyzer(
    private val fiftStdlibPath: Path,
) : TvmAnalyzer<Path> {
    private val fiftExecutablePath: Path = Paths.get(FIFT_EXECUTABLE)

    override fun convertToTvmContractCode(sources: Path): TsaContractCode {
        val tmpBocFile = createTempFile(suffix = ".boc")
        try {
            compileFiftToBoc(sources, tmpBocFile)
            return BocAnalyzer.loadContractFromBoc(tmpBocFile)
        } finally {
            tmpBocFile.deleteIfExists()
        }
    }

    /**
     * [codeBlocks] -- blocks of FIFT instructions, surrounded with <{ ... }>
     * */
    fun compileFiftCodeBlocksContract(
        fiftWorkDir: Path,
        codeBlocks: List<String>,
    ): TsaContractCode {
        val tmpBocFile = createTempFile(suffix = ".boc")
        try {
            compileFiftCodeBlocks(fiftWorkDir, codeBlocks, tmpBocFile)
            return BocAnalyzer.loadContractFromBoc(tmpBocFile)
        } finally {
            tmpBocFile.deleteIfExists()
        }
    }

    /**
     * Run method with [methodId].
     *
     * Note: the result Gas usage includes additional runvmx cost.
     * */
    fun runFiftMethod(fiftPath: Path, methodId: Int): FiftInterpreterResult {
        val fiftTextWithOutputCommand = """
        ${fiftPath.readText()}
        <s $methodId swap 0x41 runvmx $FINAL_STACK_STATE_MARKER .s
    """.trimIndent()

        return runFiftInterpreter(fiftPath.parent, fiftTextWithOutputCommand)
    }

    /**
     * [codeBlock] -- block of FIFT instructions, surrounded with <{ ... }>
     * */
    fun runFiftCodeBlock(fiftWorkDir: Path, codeBlock: String): FiftInterpreterResult {
        check(fiftWorkDir.resolve("Asm.fif").exists()) { "No Asm.fif" }
        check(fiftWorkDir.resolve("Fift.fif").exists()) { "No Fift.fif" }

        val fiftTextWithOutputCommand = """
        "Fift.fif" include
        "Asm.fif" include

        ${codeBlock.trim()}s runvmcode $FINAL_STACK_STATE_MARKER .s
    """.trimIndent()

        return runFiftInterpreter(fiftWorkDir, fiftTextWithOutputCommand)
    }

    fun compileFiftToBoc(fiftPath: Path, bocFilePath: Path) {
        val fiftCommand = "echo '\"$fiftPath\" include boc>B \"$bocFilePath\" B>file' | fift"
        performFiftCommand(fiftCommand, bocFilePath)
    }

    /**
     * [codeBlocks] -- blocks of FIFT instructions, surrounded with <{ ... }>
     * */
    private fun compileFiftCodeBlocks(fiftWorkDir: Path, codeBlocks: List<String>, bocFilePath: Path) {
        check(fiftWorkDir.resolve("Asm.fif").exists()) { "No Asm.fif" }
        check(fiftWorkDir.resolve("Fift.fif").exists()) { "No Fift.fif" }

        val methodIds = codeBlocks.indices.map { "$it DECLMETHOD cb_$it" }
        val blocks = codeBlocks.mapIndexed { index, block -> "cb_$index PROC:$block" }

        val fiftCode = """
        "Fift.fif" include
        "Asm.fif" include
        
        PROGRAM{
          ${methodIds.joinToString("\n")}
          
          ${blocks.joinToString("\n")}
        }END>c
        """.trimIndent()

        compileFiftCodeToBoc(fiftCode, bocFilePath)
    }

    private fun compileFiftCodeToBoc(fiftCode: String, bocFilePath: Path) {
        val fiftTextWithOutputCommand = """
        $fiftCode
        2 boc+>B "$bocFilePath" B>file
        """.trimIndent()

        val fiftCommand = "echo '$fiftTextWithOutputCommand' | $fiftExecutablePath -n"
        performFiftCommand(fiftCommand, bocFilePath)
    }

    private fun performFiftCommand(fiftCommand: String, bocFilePath: Path) {
        val executionCommand = fiftCommand.toExecutionCommand()
        val (exitValue, completedInTime, _, errors) = executeCommandWithTimeout(
            executionCommand,
            COMPILER_TIMEOUT,
            fiftStdlibPath.toFile()
        )

        check(completedInTime) {
            "Fift compilation has not finished in $COMPILER_TIMEOUT seconds"
        }

        check(exitValue == 0 && bocFilePath.exists() && bocFilePath.readBytes().isNotEmpty()) {
            "Fift compilation failed with an error, exit code $exitValue, errors: \n${errors.toText()}"
        }
    }

    private fun runFiftInterpreter(
        fiftWorkDir: Path,
        fiftInterpreterCommand: String
    ): FiftInterpreterResult {
        val fiftCommand = "echo '$fiftInterpreterCommand' | $fiftExecutablePath -n"
        val executionCommand = fiftCommand.toExecutionCommand()
        val (exitValue, completedInTime, output, errors) = executeCommandWithTimeout(
            executionCommand,
            COMPILER_TIMEOUT,
            fiftWorkDir.toFile(),
            mapOf("FIFTPATH" to fiftStdlibPath.toString())
        )

        check(completedInTime) {
            "`fift` process has not has not finished in $COMPILER_TIMEOUT seconds"
        }

        check(exitValue == 0) {
            "`fift` process failed with an error, exit code $exitValue, errors: \n${errors.toText()}"
        }

        val finalStackState = output
            .lastOrNull { it.trim().endsWith(FINAL_STACK_STATE_MARKER) }
            ?.trim()?.removeSuffix(FINAL_STACK_STATE_MARKER)?.trim()
            ?: error("No final stack state")

        val stackEntries = finalStackState.split(' ').map { it.trim() }
        val exitCode = stackEntries.lastOrNull()?.toIntOrNull()
            ?: error("Incorrect exit code: $finalStackState")

        val stackEntriesWithoutExitCode = stackEntries.dropLast(1)

        val tvmState = errors
            .mapNotNull { TVM_EXECUTION_STATUS_PATTERN.matchEntire(it) }
            .lastOrNull()
            ?: error("No TVM state")

        val (_, steps, gasUsage) = tvmState.groupValues

        return FiftInterpreterResult(exitCode, gasUsage.toInt(), steps.toInt(), stackEntriesWithoutExitCode)
    }

    companion object {
        private const val FINAL_STACK_STATE_MARKER = "\"FINAL STACK STATE\""
        private val TVM_EXECUTION_STATUS_PATTERN = Regex(""".*steps: (\d+) gas: used=(\d+).*""")
    }
}

data object BocAnalyzer : TvmAnalyzer<Path> {
    override fun convertToTvmContractCode(sources: Path): TsaContractCode {
        return loadContractFromBoc(sources)
    }

    fun loadContractFromBoc(bocFilePath: Path): TsaContractCode {
        return TsaContractCode.construct(bocFilePath)
    }
}

private fun runAnalysisInCatchingBlock(
    contractIdForCoverageStats: ContractId,
    contractForCoverageStats: TsaContractCode,
    methodId: MethodId,
    logInfoAboutAnalysis: Boolean = true,
    throwNotImplementedError: Boolean = false,
    analysisRun: (TvmCoverageStatistics) -> List<TvmState>,
): Pair<List<TvmState>, TvmMethodCoverage> =
    runCatching {
        val coverageStatistics = TvmCoverageStatistics(contractIdForCoverageStats, contractForCoverageStats.mainMethod)

        val states = analysisRun(coverageStatistics)

        val coverage = TvmMethodCoverage(
            coverageStatistics.getMethodCoveragePercents(methodId),
            coverageStatistics.getTransitiveCoveragePercents(),
            coverageStatistics.getMainMethodCoveragePercents(),
        )

        if (logInfoAboutAnalysis) {
            logger.info("Method {}", methodId)
            logger.info("Coverage: ${coverage.coverage}, transitive coverage: ${coverage.transitiveCoverage}, main method coverage: ${coverage.coverageOfMainMethod}")
        }
        val exceptionalStates = states.filter { state -> state.isExceptional }
        logger.debug("States: ${states.size}, exceptional: ${exceptionalStates.size}")
        exceptionalStates.forEach { state -> logger.debug(state.methodResult.toString()) }
        logger.debug("=====".repeat(20))

        states to coverage
    }.getOrElse {
        if (it is NotImplementedError && throwNotImplementedError) {
            throw it
        }

        logger.error(it) {
            "Failed analyzing method with id $methodId"
        }

        emptyList<TvmState>() to TvmMethodCoverage(coverage = 0f, transitiveCoverage = 0f, coverageOfMainMethod = 0f)
    }

fun analyzeInterContract(
    contracts: List<TsaContractCode>,
    startContractId: ContractId,
    methodId: MethodId,
    inputInfo: TvmInputInfo = TvmInputInfo(),
    additionalStopStrategy: StopStrategy = StopStrategy { false },
    additionalObserver: UMachineObserver<TvmState>? = null,
    options: TvmOptions = TvmOptions(),
    throwNotImplementedError: Boolean = false,
    manualStateProcessor: TvmManualStateProcessor = TvmManualStateProcessor(),
    concreteContractData: List<TvmConcreteContractData> = contracts.map { TvmConcreteContractData() },
): TvmSymbolicTestSuite {
    val machine = TvmMachine(tvmOptions = options)
    val startContractCode = contracts[startContractId]
    val (states, coverage) = runAnalysisInCatchingBlock(
        contractIdForCoverageStats = startContractId,
        contractForCoverageStats = startContractCode,
        methodId = methodId,
        logInfoAboutAnalysis = false,
        throwNotImplementedError = throwNotImplementedError,
    ) { coverageStatistics ->
        machine.analyze(
            contracts,
            startContractId,
            concreteGeneralData = TvmConcreteGeneralData(),
            concreteContractData = concreteContractData,
            coverageStatistics,
            methodId,
            inputInfo = inputInfo,
            additionalStopStrategy = additionalStopStrategy,
            additionalObserver = additionalObserver,
            manualStateProcessor = manualStateProcessor,
        )
    }

    machine.close()
    return TvmTestResolver.resolveSingleMethod(methodId, states, coverage)
}

fun analyzeAllMethods(
    contract: TsaContractCode,
    methodsBlackList: Set<MethodId> = hashSetOf(),
    methodWhitelist: Set<MethodId>? = null,
    concreteGeneralData: TvmConcreteGeneralData = TvmConcreteGeneralData(),
    concreteContractData: TvmConcreteContractData = TvmConcreteContractData(),
    inputInfo: Map<BigInteger, TvmInputInfo> = emptyMap(),
    tvmOptions: TvmOptions = TvmOptions(),
    takeEmptyTests: Boolean = false,
): TvmContractSymbolicTestResult {
    if (contract.methods.isEmpty()) {
        throw NoSelectedMethodsToAnalyze()
    }

    val methodsExceptDictPushConst = contract.methods.filterKeys { it !in methodsBlackList }
    val methodTests = methodsExceptDictPushConst.values.mapNotNull { method ->
        if (methodWhitelist?.let { method.id in it } == false) {
            return@mapNotNull null
        }
        analyzeSpecificMethod(
            contract,
            method.id,
            concreteGeneralData,
            concreteContractData,
            inputInfo[method.id] ?: TvmInputInfo(),
            tvmOptions
        )
    }

    return TvmTestResolver.groupTestSuites(methodTests, takeEmptyTests)
}

fun analyzeSpecificMethod(
    contract: TsaContractCode,
    methodId: MethodId,
    concreteGeneralData: TvmConcreteGeneralData = TvmConcreteGeneralData(),
    concreteContractData: TvmConcreteContractData = TvmConcreteContractData(),
    inputInfo: TvmInputInfo = TvmInputInfo(),
    tvmOptions: TvmOptions = TvmOptions(),
    manualStateProcessor: TvmManualStateProcessor = TvmManualStateProcessor(),
): TvmSymbolicTestSuite {
    val machine = TvmMachine(tvmOptions = tvmOptions)
    val (states, coverage) = machine.use {
        runAnalysisInCatchingBlock(
            contractIdForCoverageStats = 0,
            contract,
            methodId,
        ) { coverageStatistics ->
            machine.analyze(
                contract,
                concreteGeneralData,
                concreteContractData,
                coverageStatistics,
                methodId,
                inputInfo,
                manualStateProcessor,
            )
        }
    }

    return TvmTestResolver.resolveSingleMethod(methodId, states, coverage)
}

fun getFuncContract(
    path: Path,
    funcStdlibPath: Path,
    fiftStdlibPath: Path,
    isTSAChecker: Boolean = false
): TsaContractCode = FuncAnalyzer(funcStdlibPath, fiftStdlibPath).convertToTvmContractCode(path).also {
    if (isTSAChecker) {
        setTSACheckerFunctions(it)
    }
}

private fun String.toExecutionCommand(): List<String> = listOf("/bin/sh", "-c", this)

class NoSelectedMethodsToAnalyze : RuntimeException() {
    override val message: String = "No selected methods can be extracted from this contract. Please specify a method to analyze."
}

data class FiftInterpreterResult(
    val exitCode: Int,
    val gasUsage: Int,
    val steps: Int,
    val stack: List<String>
)

private const val COMPILER_TIMEOUT = 5.toLong() // seconds

@Suppress("NOTHING_TO_INLINE")
inline fun Int.toMethodId(): MethodId = toBigInteger()

private val logger = object : KLogging() {}.logger
