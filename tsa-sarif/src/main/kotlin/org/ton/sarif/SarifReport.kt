package org.ton.sarif

import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.LogicalLocation
import io.github.detekt.sarif4k.Message
import io.github.detekt.sarif4k.PropertyBag
import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.Run
import io.github.detekt.sarif4k.SarifSchema210
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.modules.SerializersModule
import org.ton.bigint.BigIntSerializer
import org.ton.bytecode.MethodId
import org.ton.bytecode.TvmContractCode
import org.usvm.machine.state.TvmResult.TvmFailure
import org.usvm.test.resolver.TvmContractSymbolicTestResult
import org.usvm.test.resolver.TvmExecutionWithSoftFailure
import org.usvm.test.resolver.TvmExecutionWithStructuralError
import org.usvm.test.resolver.TvmSuccessfulActionPhase
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmSymbolicTestSuite
import org.usvm.test.resolver.TvmTestFailure
import java.math.BigInteger

fun TvmContractSymbolicTestResult.toSarifReport(methodsMapping: Map<MethodId, String>): String =
    SarifSchema210(
        schema = TsaSarifSchema.SCHEMA,
        version = TsaSarifSchema.VERSION,
        runs =
            listOf(
                Run(
                    tool = TsaSarifSchema.TsaSarifTool.TOOL,
                    results = testSuites.flatMap { it.toSarifResult(methodsMapping) },
                    properties =
                        PropertyBag(
                            mapOf(
                                "coverage" to
                                    testSuites.associate {
                                        it.methodId.toString() to
                                            it.methodCoverage.transitiveCoverage
                                    },
                            ),
                        ),
                ),
            ),
    ).let { TvmContractCode.json.encodeToString(it) }

private val defaultSerializationModule: SerializersModule
    get() =
        SerializersModule {
            contextual(BigInteger::class, BigIntSerializer)
        }

private val json =
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        serializersModule = defaultSerializationModule
        encodeDefaults = true
    }

private fun TvmSymbolicTestSuite.toSarifResult(methodsMapping: Map<MethodId, String>): List<Result> =
    tests.toSarifResult(methodsMapping)

fun List<TvmSymbolicTest>.toSarifReport(
    methodsMapping: Map<MethodId, String>,
    useShortenedOutput: Boolean,
): String =
    SarifSchema210(
        schema = TsaSarifSchema.SCHEMA,
        version = TsaSarifSchema.VERSION,
        runs =
            listOf(
                Run(
                    tool = TsaSarifSchema.TsaSarifTool.TOOL,
                    results = toSarifResult(methodsMapping, useShortenedOutput),
                ),
            ),
    ).let { TvmContractCode.json.encodeToString(it) }

private fun List<TvmSymbolicTest>.toSarifResult(
    methodsMapping: Map<MethodId, String>,
    useShortenedOutput: Boolean = false,
) = mapNotNull {
    val (ruleId, message) =
        when (it.result) {
            is TvmTestFailure -> {
                val methodFailure = it.result as TvmTestFailure
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

            is TvmSuccessfulActionPhase -> {
                error("Unexpected result: ${it.result}")
            }
        }

    val methodId = it.methodId
    val methodName = methodsMapping[methodId]

    val properties =
        PropertyBag(
            listOfNotNull(
                "usedParameters" to json.encodeToJsonElement(it.input),
                it.fetchedValues.takeIf { it.isNotEmpty() }?.let {
                    "fetchedValues" to json.encodeToJsonElement(it)
                },
                "rootContractInitialC4" to json.encodeToJsonElement(it.rootInitialData),
                "additionalInputs" to json.encodeToJsonElement(it.additionalInputs),
                "events" to json.encodeToJsonElement(it.eventsList),
            ).toMap(),
        )

    if (useShortenedOutput) {
        Result(
            ruleID = ruleId,
            level = TsaSarifSchema.TsaSarifResult.LEVEL,
            message = Message(text = message),
            properties = properties,
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
                                                    json.encodeToJsonElement(
                                                        it.lastStmt?.physicalLocation,
                                                    ),
                                                "inst" to it.lastStmt?.mnemonic,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
            properties = properties,
        )
    }
}

private fun resolveRuleId(methodResult: TvmFailure): String = methodResult.exit.ruleName
