package org.ton.sarif

import io.github.detekt.sarif4k.CodeFlow
import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.LogicalLocation
import io.github.detekt.sarif4k.Message
import io.github.detekt.sarif4k.PropertyBag
import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.Run
import io.github.detekt.sarif4k.SarifSchema210
import io.github.detekt.sarif4k.ThreadFlow
import io.github.detekt.sarif4k.ThreadFlowLocation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.encodeToJsonElement
import org.ton.bytecode.MethodId
import org.ton.bytecode.TvmContractCode
import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmMethod
import org.usvm.machine.state.TvmMethodResult.TvmFailure
import org.usvm.test.resolver.TvmContractSymbolicTestResult
import org.usvm.test.resolver.TvmExecutionWithStructuralError
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmSymbolicTestSuite
import org.usvm.test.resolver.TvmTestInput

fun TvmContractSymbolicTestResult.toSarifReport(methodsMapping: Map<MethodId, String>): String = SarifSchema210(
    schema = TsaSarifSchema.SCHEMA,
    version = TsaSarifSchema.VERSION,
    runs = listOf(
        Run(
            tool = TsaSarifSchema.TsaSarifTool.TOOL,
            results = testSuites.flatMap { it.toSarifResult(methodsMapping) },
            properties = PropertyBag(
                mapOf(
                    "coverage" to testSuites.associate { it.methodId.toString() to it.methodCoverage.transitiveCoverage }
                )
            )
        )
    )
).let { TvmContractCode.json.encodeToString(it) }

private fun TvmSymbolicTestSuite.toSarifResult(methodsMapping: Map<MethodId, String>): List<Result> {
    return tests.toSarifResult(methodsMapping)
}

fun List<TvmSymbolicTest>.toSarifReport(
    methodsMapping: Map<MethodId, String>,
    useShortenedOutput: Boolean
): String =
    SarifSchema210(
        schema = TsaSarifSchema.SCHEMA,
        version = TsaSarifSchema.VERSION,
        runs = listOf(
            Run(
                tool = TsaSarifSchema.TsaSarifTool.TOOL,
                results = toSarifResult(methodsMapping, useShortenedOutput),
            )
        )
    ).let { TvmContractCode.json.encodeToString(it) }

private fun List<TvmSymbolicTest>.toSarifResult(
    methodsMapping: Map<MethodId, String>,
    useShortenedOutput: Boolean = false,
) = mapNotNull {
    val (ruleId, message) = when (it.result) {
        is TvmMethodFailure -> {
            val methodFailure = it.result as TvmMethodFailure
            resolveRuleId(methodFailure.failure) to methodFailure.failure.toString()
        }
        is TvmExecutionWithStructuralError -> {
            val exit = (it.result as TvmExecutionWithStructuralError).exit
            exit.ruleId to exit.toString()
        }
        is TvmSuccessfulExecution -> {
            return@mapNotNull null
        }
    }

    val methodId = it.methodId
    val methodName = methodsMapping[methodId]

    val properties = PropertyBag(
        listOfNotNull(
            "gasUsage" to it.gasUsage,
            "usedParameters" to TvmContractCode.json.encodeToJsonElement(it.input),
            it.fetchedValues.takeIf { it.isNotEmpty() }?.let {
                "fetchedValues" to TvmContractCode.json.encodeToJsonElement(it)
            },
            "resultStack" to TvmContractCode.json.encodeToJsonElement(it.result.stack),
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
            locations = listOf(
                Location(
                    logicalLocations = listOf(
                        LogicalLocation(decoratedName = methodId.toString(), fullyQualifiedName = methodName)
                    ),
                )
            ),
            codeFlows = resolveCodeFlows(it.stackTrace, methodsMapping),
            properties = properties
        )
    }
}

private fun resolveRuleId(methodResult: TvmFailure): String = methodResult.exit.ruleName

private fun resolveCodeFlows(stackTrace: List<TvmInst>, methodsMapping: Map<MethodId, String>): List<CodeFlow> {
    val threadFlows = mutableListOf<ThreadFlow>()

    for (stmt in stackTrace) {
        val method = stmt.location.codeBlock

        val methodId = (method as? TvmMethod)?.id
        val methodName = if (method is TvmMethod) {
            methodsMapping[method.id]
        } else {
            "Lambda"
        }

        val location = Location(
            logicalLocations = listOf(
                LogicalLocation(
                    decoratedName = methodId?.toString(),
                    fullyQualifiedName = methodName,
                    properties = PropertyBag(
                        mapOf(
                            "stmt" to "${stmt.mnemonic}#${stmt.location.index}",
                        )
                    )
                )
            ),
        )
        val threadFlowLocation = ThreadFlowLocation(location = location)

        threadFlows += ThreadFlow(locations = listOf(threadFlowLocation))
    }

    val codeFlow = CodeFlow(threadFlows = threadFlows)

    return listOf(codeFlow)
}
