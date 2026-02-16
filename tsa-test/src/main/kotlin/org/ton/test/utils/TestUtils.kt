package org.ton.test.utils

import one.profiler.AsyncProfiler
import org.ton.TlbCompositeLabel
import org.ton.TvmContractHandlers
import org.ton.TvmInputInfo
import org.ton.TvmParameterInfo
import org.ton.boc.BagOfCells
import org.ton.bytecode.MethodId
import org.ton.bytecode.TsaContractCode
import org.ton.cell.Cell
import org.ton.communicationSchemeFromJson
import org.ton.tlb.readFromJson
import org.usvm.machine.BocAnalyzer
import org.usvm.machine.FiftAnalyzer
import org.usvm.machine.FiftInterpreterResult
import org.usvm.machine.FuncAnalyzer
import org.usvm.machine.TactAnalyzer
import org.usvm.machine.TactAnalyzer.Companion.DEFAULT_TACT_EXECUTABLE
import org.usvm.machine.TactSourcesDescription
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmConcreteGeneralData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.machine.getResourcePath
import org.usvm.machine.intValue
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmResult
import org.usvm.machine.state.TvmStack
import org.usvm.machine.toMethodId
import org.usvm.machine.types.TvmIntegerType
import org.usvm.test.resolver.TvmContractSymbolicTestResult
import org.usvm.test.resolver.TvmExecutionWithSoftFailure
import org.usvm.test.resolver.TvmExecutionWithStructuralError
import org.usvm.test.resolver.TvmSuccessfulActionPhase
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmSymbolicTestSuite
import org.usvm.test.resolver.TvmTerminalMethodSymbolicResult
import org.usvm.test.resolver.TvmTestFailure
import org.usvm.test.resolver.TvmTestIntegerValue
import org.usvm.test.resolver.TvmTestNullValue
import org.usvm.test.resolver.TvmTestTupleValue
import org.usvm.test.resolver.TvmTestValue
import java.math.BigInteger
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.jvm.java
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Options for tests with concrete execution
val testConcreteOptions =
    TvmOptions(
        turnOnTLBParsingChecks = false,
        useReceiverInputs = false,
        enableInputValues = false,
        useMainMethodForInitialMethodJump = false,
    )

val testOptionsToAnalyzeSpecificMethod = TvmOptions(useReceiverInputs = false)

fun extractResource(resourcePath: String) = getResourcePath(object {}.javaClass, resourcePath)

// On Windows, this might be [tact.cmd] instead of [tact]
val tactExecutable = System.getenv("TACT_EXECUTABLE") ?: DEFAULT_TACT_EXECUTABLE

fun tactCompileAndAnalyzeAllMethods(
    tactSources: TactSourcesDescription,
    concreteGeneralData: TvmConcreteGeneralData = TvmConcreteGeneralData(),
    concreteContractData: TvmConcreteContractData = TvmConcreteContractData(),
    methodsBlackList: Set<MethodId> = hashSetOf(),
    methodWhiteList: Set<MethodId>? = null,
    inputInfo: Map<MethodId, TvmInputInfo> = emptyMap(),
    tvmOptions: TvmOptions = TvmOptions(),
    takeEmptyTests: Boolean = false,
): TvmContractSymbolicTestResult =
    TactAnalyzer(tactExecutable).analyzeAllMethods(
        tactSources,
        concreteGeneralData,
        concreteContractData,
        methodsBlackList,
        methodWhiteList,
        inputInfo,
        tvmOptions,
        takeEmptyTests,
    )

val funcAnalyzer = FuncAnalyzer(fiftStdlibPath = FIFT_STDLIB_RESOURCE)

fun funcCompileAndAnalyzeAllMethods(
    funcSourcesPath: Path,
    concreteGeneralData: TvmConcreteGeneralData = TvmConcreteGeneralData(),
    concreteContractData: TvmConcreteContractData = TvmConcreteContractData(),
    methodsBlackList: Set<MethodId> = hashSetOf(),
    methodWhiteList: Set<MethodId>? = null,
    inputInfo: Map<MethodId, TvmInputInfo> = emptyMap(),
    tvmOptions: TvmOptions = TvmOptions(),
): TvmContractSymbolicTestResult =
    funcAnalyzer.analyzeAllMethods(
        funcSourcesPath,
        concreteGeneralData,
        concreteContractData,
        methodsBlackList,
        methodWhiteList,
        inputInfo,
        tvmOptions,
    )

fun funcCompileAndAnalyzeSpecificMethod(
    funcSourcesPath: Path,
    methodId: MethodId,
    concreteGeneralData: TvmConcreteGeneralData = TvmConcreteGeneralData(),
    concreteContractData: TvmConcreteContractData = TvmConcreteContractData(),
    inputInfo: TvmInputInfo = TvmInputInfo(emptyMap()),
    tvmOptions: TvmOptions = TvmOptions(),
): TvmSymbolicTestSuite =
    funcAnalyzer.analyzeSpecificMethod(
        funcSourcesPath,
        methodId,
        concreteGeneralData,
        concreteContractData,
        inputInfo,
        tvmOptions,
    )

fun compileAndAnalyzeFift(
    fiftPath: Path,
    concreteGeneralData: TvmConcreteGeneralData = TvmConcreteGeneralData(),
    concreteContractData: TvmConcreteContractData = TvmConcreteContractData(),
    methodsBlackList: Set<MethodId> = hashSetOf(),
    methodWhiteList: Set<MethodId>? = null,
    inputInfo: Map<MethodId, TvmInputInfo> = emptyMap(),
    tvmOptions: TvmOptions = TvmOptions(),
): TvmContractSymbolicTestResult =
    FiftAnalyzer(fiftStdlibPath = FIFT_STDLIB_RESOURCE).analyzeAllMethods(
        fiftPath,
        concreteGeneralData,
        concreteContractData,
        methodsBlackList,
        methodWhiteList,
        inputInfo,
        tvmOptions,
    )

fun compileAndAnalyzeFift(
    fiftPath: Path,
    methodId: MethodId,
    concreteGeneralData: TvmConcreteGeneralData = TvmConcreteGeneralData(),
    concreteContractData: TvmConcreteContractData = TvmConcreteContractData(),
    inputInfo: TvmInputInfo = TvmInputInfo(),
    tvmOptions: TvmOptions = TvmOptions(),
): TvmSymbolicTestSuite =
    FiftAnalyzer(fiftStdlibPath = FIFT_STDLIB_RESOURCE).analyzeSpecificMethod(
        fiftPath,
        methodId,
        concreteGeneralData,
        concreteContractData,
        inputInfo,
        tvmOptions,
    )

/**
 * [codeBlocks] -- blocks of FIFT instructions, surrounded with <{ ... }>
 * */
fun compileFiftCodeBlocksContract(
    fiftWorkDir: Path,
    codeBlocks: List<String>,
): TsaContractCode =
    FiftAnalyzer(
        fiftStdlibPath = FIFT_STDLIB_RESOURCE,
    ).compileFiftCodeBlocksContract(fiftWorkDir, codeBlocks)

fun compileFuncToFift(
    funcSourcesPath: Path,
    fiftFilePath: Path,
) = FuncAnalyzer(
    fiftStdlibPath = FIFT_STDLIB_RESOURCE,
).compileFuncSourceToFift(funcSourcesPath, fiftFilePath)

fun analyzeAllMethods(
    bytecodePath: String,
    concreteGeneralData: TvmConcreteGeneralData = TvmConcreteGeneralData(),
    concreteContractData: TvmConcreteContractData = TvmConcreteContractData(),
    methodsBlackList: Set<MethodId> = hashSetOf(),
    methodWhiteList: Set<MethodId>? = null,
    inputInfo: Map<MethodId, TvmInputInfo> = emptyMap(),
    options: TvmOptions = TvmOptions(),
): TvmContractSymbolicTestResult =
    BocAnalyzer.analyzeAllMethods(
        Path(bytecodePath),
        concreteGeneralData,
        concreteContractData,
        methodsBlackList,
        methodWhiteList,
        inputInfo,
        options,
    )

fun analyzeFuncIntercontract(
    sources: List<Path>,
    startContract: ContractId = 0,
    options: TvmOptions,
): TvmSymbolicTestSuite {
    val contracts = sources.map { getFuncContract(it, FIFT_STDLIB_RESOURCE) }

    return analyzeInterContract(
        contracts = contracts,
        startContractId = startContract,
        methodId = TvmContext.RECEIVE_INTERNAL_ID,
        options = options,
    )
}

/**
 * Run method with [methodId].
 *
 * Note: the result Gas usage includes additional runvmx cost.
 * */
fun runFiftMethod(
    fiftPath: Path,
    methodId: Int,
): FiftInterpreterResult =
    FiftAnalyzer(
        fiftStdlibPath = FIFT_STDLIB_RESOURCE,
    ).runFiftMethod(fiftPath, methodId)

/**
 * [codeBlock] -- block of FIFT instructions, surrounded with <{ ... }>
 * */
fun runFiftCodeBlock(
    fiftWorkDir: Path,
    codeBlock: String,
): FiftInterpreterResult =
    FiftAnalyzer(
        fiftStdlibPath = FIFT_STDLIB_RESOURCE,
    ).runFiftCodeBlock(fiftWorkDir, codeBlock)

fun getAddressBits(addressInRawForm: String): String {
    val hexPart = addressInRawForm.drop(2)
    return "1" + "0".repeat(10) + BigInteger(hexPart, 16).toString(2).padStart(256, '0')
}

internal fun TvmStack.loadIntegers(n: Int) =
    List(n) {
        takeLast(TvmIntegerType) { error("Impossible") }.intValue?.intValue()
            ?: error("Unexpected entry type")
    }.reversed()

internal fun TvmSymbolicTest.executionCode(): Int? =
    when (val casted = result) {
        is TvmTerminalMethodSymbolicResult -> casted.exitCode

        is TvmExecutionWithStructuralError, is TvmExecutionWithSoftFailure -> null

        // execution interrupted
        is TvmSuccessfulActionPhase -> error("Unexpected result: $result")
    }

internal fun compareSymbolicAndConcreteResults(
    methodIds: Set<Int>,
    symbolicResult: TvmContractSymbolicTestResult,
    expectedState: (Int) -> FiftInterpreterResult,
) = compareSymbolicAndConcreteResults(
    methodIds,
    symbolicResult,
    expectedState,
    symbolicStack = { symbolicTest ->
        (symbolicTest.result as? TvmSuccessfulExecution)?.stack
            ?: emptyList()
    },
    concreteStackBlock = { fiftResult ->
        val result = mutableListOf<TvmTestValue>()
        parseFiftStack(fiftResult.stack, result, initialIndex = 0)
        result
    },
)

internal fun compareSymbolicAndConcreteResultsFunc(
    resourcePath: String,
    methods: Set<Int>,
) {
    val contractPath = extractResource(resourcePath)
    val tmpFiftFile = kotlin.io.path.createTempFile(suffix = ".fif")

    try {
        compileFuncToFift(contractPath, tmpFiftFile)

        val symbolicResult =
            compileAndAnalyzeFift(
                tmpFiftFile,
                methodWhiteList = methods.map { it.toMethodId() }.toSet(),
                tvmOptions = testConcreteOptions,
            )

        compareSymbolicAndConcreteResults(methods, symbolicResult) { methodId ->
            runFiftMethod(tmpFiftFile, methodId)
        }
    } finally {
        tmpFiftFile.deleteIfExists()
    }
}

internal fun compareSymbolicAndConcreteResultsFift(
    fiftPath: String,
    lastIndex: Int,
) {
    val fiftResourcePath = extractResource(fiftPath)

    val symbolicResult =
        compileAndAnalyzeFift(
            fiftResourcePath,
            tvmOptions = testConcreteOptions,
        )
    val methodIds = (0..lastIndex).toSet()

    compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
        runFiftMethod(fiftResourcePath, methodId)
    }
}

private fun parseFiftStack(
    entries: List<String>,
    result: MutableList<TvmTestValue>,
    initialIndex: Int,
): Int {
    var index = initialIndex
    while (index < entries.size) {
        when (entries[index]) {
            "[" -> {
                // tuple start
                val tupleElements = mutableListOf<TvmTestValue>()
                index = parseFiftStack(entries, tupleElements, index + 1)
                result += TvmTestTupleValue(tupleElements)
            }

            "]" -> {
                // tuple end
                return index + 1
            }

            "(null)" -> {
                result += TvmTestNullValue
                index++
            }

            else -> {
                val number = entries[index].toBigInteger()
                result += TvmTestIntegerValue(number)
                index++
            }
        }
    }

    return index
}

internal fun <T> compareSymbolicAndConcreteResults(
    methodIds: Set<Int>,
    symbolicResult: TvmContractSymbolicTestResult,
    expectedResult: (Int) -> FiftInterpreterResult,
    symbolicStack: (TvmSymbolicTest) -> List<T>,
    concreteStackBlock: (FiftInterpreterResult) -> List<T>,
) = compareMethodStates(methodIds, symbolicResult, expectedResult) { methodId, symbolicTest, concreteResult ->
    val actualStatus = symbolicTest.executionCode()
    assertEquals(concreteResult.exitCode, actualStatus, "Wrong exit code for method id: $methodId")

    if (concreteResult.exitCode != 0) {
        return@compareMethodStates
    }

    val concreteStackValue = concreteStackBlock(concreteResult)
    val actualStack = symbolicStack(symbolicTest)
    assertEquals(concreteStackValue, actualStack, "Wrong stack for method id: $methodId")
}

internal fun compareMethodStates(
    methodIds: Set<Int>,
    symbolicResult: TvmContractSymbolicTestResult,
    expectedResult: (Int) -> FiftInterpreterResult,
    comparison: (Int, TvmSymbolicTest, FiftInterpreterResult) -> Unit,
) {
    assertEquals(methodIds, symbolicResult.testSuites.mapTo(hashSetOf()) { it.methodId.toInt() })

    for ((method, _, tests) in symbolicResult.testSuites) {
        val test = tests.single()
        val methodId = method.toInt()
        val concreteResult = expectedResult(methodId)
        comparison(methodId, test, concreteResult)
    }
}

internal fun checkAtLeastOneStateForAllMethods(
    methodsNumber: Int,
    symbolicResult: TvmContractSymbolicTestResult,
) {
    assertEquals(methodsNumber, symbolicResult.size)
    assertTrue(symbolicResult.all { it.tests.isNotEmpty() })
}

internal fun propertiesFound(
    testSuite: TvmSymbolicTestSuite,
    properties: List<(TvmSymbolicTest) -> Boolean>,
) {
    val failedProperties = mutableListOf<Int>()
    properties.forEachIndexed outer@{ index, property ->
        testSuite.tests.forEach { test ->
            if (property(test)) {
                return@outer
            }
        }
        failedProperties.add(index)
    }
    assertTrue(failedProperties.isEmpty(), "Properties $failedProperties were not found")
}

internal fun TvmSymbolicTestSuite.assertPropertiesFound(vararg properties: (TvmSymbolicTest) -> Boolean) =
    propertiesFound(this, properties.toList())

internal fun checkInvariants(
    tests: List<TvmSymbolicTest>,
    properties: List<(TvmSymbolicTest) -> Boolean>,
) {
    val failedInvariants = mutableListOf<Int>()
    properties.forEachIndexed outer@{ index, property ->
        tests.forEach { test ->
            if (!property(test)) {
                failedInvariants.add(index)
                return@outer
            }
        }
    }
    assertTrue(failedInvariants.isEmpty(), "Invariants $failedInvariants were violated")
}

internal fun List<TvmSymbolicTest>.assertInvariantsHold(vararg properties: (TvmSymbolicTest) -> Boolean) {
    checkInvariants(this, properties.toList())
}

internal val TvmSymbolicTest.softFailure: TvmResult.TvmSoftFailureExit?
    get() = (result as? TvmExecutionWithSoftFailure)?.failure?.exit

internal fun extractTlbInfo(
    typesPath: String,
    callerClass: KClass<*>,
): Map<MethodId, TvmInputInfo> {
    val path = getResourcePath(callerClass::class.java, typesPath)
    val struct =
        readFromJson(path, "InternalMsgBody") as? TlbCompositeLabel
            ?: error("Couldn't parse TL-B structure")
    val info =
        TvmParameterInfo.SliceInfo(
            TvmParameterInfo.DataCellInfo(
                struct,
            ),
        )
    return mapOf(BigInteger.ZERO to TvmInputInfo(mapOf(0 to info)))
}

internal fun compareSymbolicAndConcreteFromResource(
    testPath: String,
    lastMethodIndex: Int,
) {
    val fiftResourcePath = extractResource(testPath)

    val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

    val methodIds = (0..lastMethodIndex).toSet()
    compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
        runFiftMethod(fiftResourcePath, methodId)
    }
}

internal fun extractCheckerContractFromResource(checkerResourcePath: String): TsaContractCode {
    val checkerPath = extractResource(checkerResourcePath)
    val checkerContract = getFuncContract(checkerPath, FIFT_STDLIB_RESOURCE, isTSAChecker = true)
    return checkerContract
}

internal fun extractFuncContractFromResource(contractResourcePath: String): TsaContractCode {
    val contractPath = extractResource(contractResourcePath)
    val checkerContract = getFuncContract(contractPath, FIFT_STDLIB_RESOURCE)
    return checkerContract
}

internal fun extractBocContractFromResource(contractResourcePath: String): TsaContractCode {
    val contractPath = extractResource(contractResourcePath)
    val checkerContract = BocAnalyzer.loadContractFromBoc(contractPath)
    return checkerContract
}

internal fun extractCommunicationSchemeFromResource(
    communicationSchemeResourcePath: String,
): Map<ContractId, TvmContractHandlers> {
    val communicationSchemePath = extractResource(communicationSchemeResourcePath)
    val communicationScheme = communicationSchemeFromJson(communicationSchemePath.readText())
    return communicationScheme
}

internal fun extractConcreteDataFromResource(dataResourcePath: String): Cell {
    val path = extractResource(dataResourcePath)
    return BagOfCells(path.toFile().readBytes()).roots.single()
}

fun TvmSymbolicTest.exitCode(): Int? =
    when (result) {
        is TvmExecutionWithSoftFailure -> null
        is TvmExecutionWithStructuralError -> null
        is TvmTestFailure -> (result as TvmTestFailure).exitCode
        is TvmSuccessfulExecution -> 0
        is TvmSuccessfulActionPhase -> error("Unexpected result: $result")
    }

/**
 * Utility function to easily profile a given test.
 * Performs an async profile run on the test and prints the output to the [outputFileName] parameter.
 * The expected usage: if you want to profile a test:
 * ```kotlin
 * fun `some kind of test`() { ... }
 * ```
 *
 * you rewrite it as:
 * ```kotlin
 * fun `some kind of test`() = withProfiler("profile.jfr") { ... }
 * ```
 * This function should only be used during testing and its usages should not be committed to the repository.
 * @param outputFileName relative path for the profiler output with a "jfr" extension
 */
@Suppress("Unused")
fun withProfiler(
    outputFileName: String,
    test: () -> Unit,
) {
    val profiler = AsyncProfiler.getInstance()
    profiler.execute("start,jfr,event=cpu,file=$outputFileName")
    test()
    profiler.execute("stop")
}

/**
 * Same as `withProfiler` but with a default name of the file "./profile.jfr"
 * @see withProfiler
 */
@Suppress("Unused")
fun withProfilerDefault(test: () -> Unit) = withProfiler("./profile.jfr", test)
