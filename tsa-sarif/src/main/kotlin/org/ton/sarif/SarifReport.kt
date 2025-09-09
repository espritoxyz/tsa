package org.ton.sarif

import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.LogicalLocation
import io.github.detekt.sarif4k.Message
import io.github.detekt.sarif4k.PropertyBag
import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.Run
import io.github.detekt.sarif4k.SarifSchema210
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.encodeToJsonElement
import org.ton.bytecode.MethodId
import org.ton.bytecode.TvmContractCode
import org.usvm.machine.state.TvmMethodResult.TvmFailure
import org.usvm.machine.state.TvmUserDefinedFailure
import org.usvm.test.resolver.TvmContractSymbolicTestResult
import org.usvm.test.resolver.TvmExecutionWithSoftFailure
import org.usvm.test.resolver.TvmExecutionWithStructuralError
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmSymbolicTestSuite

fun TvmContractSymbolicTestResult.toSarifReport(
    methodsMapping: Map<MethodId, String>,
    excludeUserDefinedErrors: Boolean = false,
): String =
    SarifSchema210(
        schema = TsaSarifSchema.SCHEMA,
        version = TsaSarifSchema.VERSION,
        runs =
            listOf(
                Run(
                    tool = TsaSarifSchema.TsaSarifTool.TOOL,
                    results = testSuites.flatMap { it.toSarifResult(methodsMapping, excludeUserDefinedErrors) },
                    properties =
                        PropertyBag(
                            mapOf(
                                "coverage" to
                                    testSuites.associate {
                                        it.methodId.toString() to
                                            it.methodCoverage.transitiveCoverage
                                    }
                            )
                        )
                )
            )
    ).let { TvmContractCode.json.encodeToString(it) }

private fun TvmSymbolicTestSuite.toSarifResult(
    methodsMapping: Map<MethodId, String>,
    excludeUserDefinedErrors: Boolean,
): List<Result> = tests.toSarifResult(methodsMapping, excludeUserDefinedErrors = excludeUserDefinedErrors)

fun List<TvmSymbolicTest>.toSarifReport(
    methodsMapping: Map<MethodId, String>,
    useShortenedOutput: Boolean,
    excludeUserDefinedErrors: Boolean,
): String =
    SarifSchema210(
        schema = TsaSarifSchema.SCHEMA,
        version = TsaSarifSchema.VERSION,
        runs =
            listOf(
                Run(
                    tool = TsaSarifSchema.TsaSarifTool.TOOL,
                    results = toSarifResult(methodsMapping, excludeUserDefinedErrors, useShortenedOutput)
                )
            )
    ).let { TvmContractCode.json.encodeToString(it) }

private fun List<TvmSymbolicTest>.toSarifResult(
    methodsMapping: Map<MethodId, String>,
    excludeUserDefinedErrors: Boolean,
    useShortenedOutput: Boolean = false,
) = mapNotNull {
    val (ruleId, message) =
        when (it.result) {
            is TvmMethodFailure -> {
                val methodFailure = it.result as TvmMethodFailure
                if (methodFailure.failure.exit is TvmUserDefinedFailure && excludeUserDefinedErrors) {
                    return@mapNotNull null
                }
                resolveRuleId(methodFailure.failure) to methodFailure.failure.toString()
            }

            is TvmExecutionWithStructuralError -> {
                val exit = (it.result as TvmExecutionWithStructuralError).exit
                exit.ruleId to exit.toString()
            }

            is TvmExecutionWithSoftFailure -> {
                val exit = (it.result as TvmExecutionWithSoftFailure).failure
                exit.exit.ruleId to exit.toString()
            }

            is TvmSuccessfulExecution -> {
                return@mapNotNull null
            }
        }

    val methodId = it.methodId
    val methodName = methodsMapping[methodId]

    val properties =
        PropertyBag(
            listOfNotNull(
                "gasUsage" to it.gasUsage,
                "usedParameters" to TvmContractCode.json.encodeToJsonElement(it.input),
                it.fetchedValues.takeIf { it.isNotEmpty() }?.let {
                    "fetchedValues" to TvmContractCode.json.encodeToJsonElement(it)
                },
                "rootContractInitialC4" to TvmContractCode.json.encodeToJsonElement(it.rootInitialData),
                "resultStack" to TvmContractCode.json.encodeToJsonElement(it.result.stack),
                "additionalInputs" to TvmContractCode.json.encodeToJsonElement(it.additionalInputs)
            ).toMap()
        )

    if (useShortenedOutput) {
        Result(
            ruleID = ruleId,
            level = TsaSarifSchema.TsaSarifResult.LEVEL,
            message = Message(text = message),
            properties = properties
        )
    } else {
        Result(
            ruleID = ruleId,
            level = TsaSarifSchema.TsaSarifResult.LEVEL,
            message = Message(text = message),
            locations =
                listOf(
                    Location(
                        logicalLocations =
                            listOf(
                                LogicalLocation(
                                    decoratedName = methodId.toString(),
                                    fullyQualifiedName = methodName,
                                    properties =
                                        PropertyBag(
                                            mapOf(
                                                "position" to
                                                    TvmContractCode.json.encodeToJsonElement(
                                                        it.lastStmt?.physicalLocation
                                                    ),
                                                "inst" to it.lastStmt?.mnemonic
                                            )
                                        )
                                )
                            )
                    )
                ),
            properties = properties
        )
    }
}

private fun resolveRuleId(methodResult: TvmFailure): String = methodResult.exit.ruleName
