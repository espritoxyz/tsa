package org.ton.common

import org.ton.TvmContractHandlers
import org.ton.TvmInputInfo
import org.ton.bytecode.TsaContractCode
import org.ton.communicationSchemeFromJson
import org.ton.options.AllMethods
import org.ton.options.AnalysisOptions
import org.ton.options.AnalysisTarget
import org.ton.options.Receivers
import org.ton.options.SarifOptions
import org.ton.options.SpecificMethod
import org.ton.options.TlbCLIOptions
import org.usvm.machine.ExploreExitCodesStopStrategy
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.NoAdditionalStopStrategy
import org.usvm.machine.TimeDivisionBetweenOpcodes
import org.usvm.machine.TvmAnalyzer
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.hexToCell
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmUserDefinedFailure
import org.usvm.machine.toMethodId
import org.usvm.test.resolver.TvmContractSymbolicTestResult
import org.usvm.test.resolver.TvmExecutionWithSoftFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmSymbolicTestSuite
import org.usvm.test.resolver.TvmTestFailure
import java.math.BigInteger
import java.nio.file.Path
import kotlin.collections.filter
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.seconds

private fun Path.extractIntercontractScheme(): Map<ContractId, TvmContractHandlers> =
    communicationSchemeFromJson(readText())

private fun createTvmOptions(
    analysisOptions: AnalysisOptions,
    interContractSchemePath: Path?,
    turnOnTLBParsingChecks: Boolean,
    useReceiverInput: Boolean,
    enableOutMessageAnalysisIfSingleContract: Boolean,
    opcodes: List<Long> = emptyList(),
): TvmOptions {
    val divideTimeBetweenOpcodes =
        if (opcodes.isEmpty()) {
            null
        } else {
            TimeDivisionBetweenOpcodes(inputId = 0, opcodes.map { it.toBigInteger() }.toSet())
        }

    val options =
        TvmOptions(
            quietMode = true,
            turnOnTLBParsingChecks = turnOnTLBParsingChecks,
            analyzeBouncedMessaged = analysisOptions.analyzeBouncedMessages,
            timeout = analysisOptions.timeout?.seconds ?: INFINITE,
            stopOnFirstError = !analysisOptions.continueOnContractException,
            useReceiverInputs = useReceiverInput,
            maxRecursionDepth = if (analysisOptions.noRecursionDepthLimit) null else analysisOptions.maxRecursionDepth,
            loopIterationLimit = if (analysisOptions.noIterationLimit) null else analysisOptions.iterationLimit,
            enableOutMessageAnalysis = enableOutMessageAnalysisIfSingleContract,
            divideTimeBetweenOpcodes = divideTimeBetweenOpcodes,
        )

    if (interContractSchemePath != null) {
        return options.copy(
            intercontractOptions =
                IntercontractOptions(
                    communicationScheme = interContractSchemePath.extractIntercontractScheme(),
                ),
            enableOutMessageAnalysis = true,
        )
    }

    return options
}

fun <SourcesDescription> performAnalysis(
    analyzer: TvmAnalyzer<SourcesDescription>,
    sources: SourcesDescription,
    contractData: String?,
    target: AnalysisTarget,
    tlbOptions: TlbCLIOptions,
    analysisOptions: AnalysisOptions,
    sarifOptions: SarifOptions,
): TvmContractSymbolicTestResult {
    val options =
        createTvmOptions(
            analysisOptions,
            interContractSchemePath = null,
            turnOnTLBParsingChecks = !tlbOptions.doNotPerformTlbChecks,
            useReceiverInput = true,
            enableOutMessageAnalysisIfSingleContract = false,
        )

    val inputInfo = TlbCLIOptions.extractInputInfo(tlbOptions.tlbJsonPath)
    val methodIds: List<BigInteger>? =
        when (target) {
            is AllMethods -> null
            is SpecificMethod -> listOf(target.methodId.toMethodId())
            is Receivers -> listOf(TvmContext.RECEIVE_INTERNAL_ID, TvmContext.RECEIVE_EXTERNAL_ID)
        }

    val concreteData = TvmConcreteContractData(contractC4 = contractData?.hexToCell())

    val additionalStopStrategy =
        if (analysisOptions.stopWhenExitCodesFound.isNotEmpty()) {
            ExploreExitCodesStopStrategy(analysisOptions.stopWhenExitCodesFound.toSet())
        } else {
            NoAdditionalStopStrategy
        }

    val result =
        if (methodIds == null) {
            analyzer.analyzeAllMethods(
                sources,
                concreteContractData = concreteData,
                inputInfo = inputInfo,
                tvmOptions = options,
                additionalStopStrategy = additionalStopStrategy,
            )
        } else {
            val testSets =
                methodIds.map { methodId ->
                    analyzer.analyzeSpecificMethod(
                        sources,
                        methodId,
                        concreteContractData = concreteData,
                        inputInfo = inputInfo[methodId] ?: TvmInputInfo(),
                        tvmOptions = options,
                        additionalStopStrategy = additionalStopStrategy,
                    )
                }

            TvmContractSymbolicTestResult(testSets)
        }

    writeCoveredInstructions(analysisOptions, result)

    return TvmContractSymbolicTestResult(result.testSuites.map { testSuite -> filterTests(testSuite, sarifOptions) })
}

fun performAnalysisInterContract(
    contracts: List<TsaContractCode>,
    concreteContractData: List<TvmConcreteContractData>,
    interContractSchemePath: Path?,
    startContractId: ContractId,
    methodId: BigInteger,
    inputInfo: TvmInputInfo,
    analysisOptions: AnalysisOptions,
    turnOnTLBParsingChecks: Boolean,
    useReceiverInput: Boolean,
    sarifOptions: SarifOptions,
    opcodes: List<Long> = emptyList(),
): TvmSymbolicTestSuite {
    val options =
        createTvmOptions(
            analysisOptions,
            interContractSchemePath,
            turnOnTLBParsingChecks,
            useReceiverInput,
            enableOutMessageAnalysisIfSingleContract = true,
            opcodes,
        )

    val additionalStopStrategy =
        if (analysisOptions.stopWhenExitCodesFound.isNotEmpty()) {
            ExploreExitCodesStopStrategy(analysisOptions.stopWhenExitCodesFound.toSet())
        } else {
            NoAdditionalStopStrategy
        }

    val result =
        analyzeInterContract(
            contracts,
            startContractId = startContractId,
            methodId = methodId,
            options = options,
            inputInfo = inputInfo,
            concreteContractData = concreteContractData,
            additionalStopStrategy = additionalStopStrategy,
        )

    writeCoveredInstructions(analysisOptions, result)

    return filterTests(result, sarifOptions)
}

private fun filterTests(
    testSuite: TvmSymbolicTestSuite,
    sarifOptions: SarifOptions,
): TvmSymbolicTestSuite {
    val filteredResult =
        TvmSymbolicTestSuite(
            testSuite.methodId,
            testSuite.methodCoverage,
            testSuite.tests.filter {
                val result = it.result
                val softFailurePasses =
                    !sarifOptions.excludeUserDefinedErrors ||
                        result !is TvmExecutionWithSoftFailure
                val userDefinedErrorPasses =
                    !sarifOptions.excludeSoftFailures ||
                        result !is TvmTestFailure ||
                        result.failure.exit is TvmUserDefinedFailure
                val isNotSuccessfullExecution =
                    result !is TvmSuccessfulExecution
                softFailurePasses && userDefinedErrorPasses && isNotSuccessfullExecution
            },
        )
    return filteredResult
}
