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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.modules.SerializersModule
import org.ton.bigint.BigIntSerializer
import org.ton.bytecode.MethodId
import org.ton.bytecode.TvmContractCode
import org.usvm.machine.interpreter.AuthAnalysisResult
import org.usvm.machine.state.TvmResult.TvmFailure
import org.usvm.machine.toBase64
import org.usvm.test.resolver.TvmContractSymbolicTestResult
import org.usvm.test.resolver.TvmExecutionWithSoftFailure
import org.usvm.test.resolver.TvmExecutionWithStructuralError
import org.usvm.test.resolver.TvmSuccessfulActionPhase
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmSymbolicTestFull
import org.usvm.test.resolver.TvmSymbolicTestSuite
import org.usvm.test.resolver.TvmTestAuthValue
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
) = mapNotNull { test ->
    val (ruleId, message) =
        when (test.result) {
            is TvmTestFailure -> {
                val methodFailure = test.result as TvmTestFailure
                resolveRuleId(methodFailure.failure) to methodFailure.failure.toString()
            }

            is TvmExecutionWithStructuralError -> {
                val exit = (test.result as TvmExecutionWithStructuralError).exit
                exit.ruleId to exit.toString()
            }

            is TvmExecutionWithSoftFailure -> {
                val exit = (test.result as TvmExecutionWithSoftFailure).failure
                exit.exit.ruleId to exit.toString()
            }

            is TvmSuccessfulExecution -> {
                error("Should accept tests where successfull execution is filtered")
            }

            is TvmSuccessfulActionPhase -> {
                error("Unexpected result: ${test.result}")
            }
        }

    val methodId = test.methodId
    val methodName = methodsMapping[methodId]

    val authValues = test.resolvedAuthValues
    val resultAuthValue = convertAuthValuesToJson(authValues)
    val properties =
        PropertyBag(
            listOfNotNull(
                (test as? TvmSymbolicTestFull)?.let { fullTest ->
                    "usedParameters" to json.encodeToJsonElement(fullTest.input)
                },
                test.fetchedValues.takeIf { it.isNotEmpty() }?.let {
                    "fetchedValues" to json.encodeToJsonElement(it)
                },
                (test as? TvmSymbolicTestFull)?.let { fullTest ->
                    "rootContractInitialC4" to json.encodeToJsonElement(fullTest.rootInitialData)
                },
                (test as? TvmSymbolicTestFull)?.let { fullTest ->
                    "additionalInputs" to json.encodeToJsonElement(fullTest.additionalInputs)
                },
                (test as? TvmSymbolicTestFull)?.let { fullTest ->
                    "events" to json.encodeToJsonElement(fullTest.eventsList)
                },
                (test as? TvmSymbolicTestFull)?.let { fullTest ->
                    "initialBalance" to
                        json.encodeToJsonElement(
                            fullTest.contractStatesBefore
                                .map { (contractId, contractState) -> contractId to contractState.balance }
                                .toMap(),
                        )
                },
                "msgIds" to json.encodeToJsonElement(test.messageIdentifierMapping),
                "authAnalysis" to json.encodeToJsonElement(resultAuthValue),
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
                                                        test.lastStmt?.physicalLocation,
                                                    ),
                                                "inst" to test.lastStmt?.mnemonic,
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

private fun convertAuthValuesToJson(authValues: AuthAnalysisResult): JsonElement =
    when (authValues) {
        is AuthAnalysisResult.Collected -> {
            val authorizedEntities =
                authValues.authorizedEntities.map {
                    when (it) {
                        is TvmTestAuthValue.AuthorizedCode -> {
                            val cellBase64 = it.code.toBase64()
                            Json.encodeToJsonElement(
                                mapOf(
                                    "type" to "code",
                                    "code" to cellBase64,
                                ),
                            )
                        }

                        is TvmTestAuthValue.AuthorizedOwner -> {
                            Json.encodeToJsonElement(
                                mapOf(
                                    "type" to "owner",
                                    "address" to "0:${it.accountId.value.toString(16).padStart(64, '0')}",
                                ),
                            )
                        }
                    }
                }
            Json.encodeToJsonElement(
                mapOf(
                    "entities" to Json.encodeToJsonElement(authorizedEntities),
                    "limit" to Json.encodeToJsonElement(authValues.collectionLimit),
                ),
            )
        }

        AuthAnalysisResult.NotCollected -> {
            Json.encodeToJsonElement("Not collected")
        }

        AuthAnalysisResult.Unknown -> {
            Json.encodeToJsonElement("Unknown")
        }
    }

private fun resolveRuleId(methodResult: TvmFailure): String = methodResult.exit.ruleName
