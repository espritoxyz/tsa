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
import org.ton.bytecode.TvmContractCode
import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmMethod
import org.usvm.machine.state.TvmMethodResult.TvmFailure
import org.usvm.test.resolver.TvmContractSymbolicTestResult
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSymbolicTestSuite
import java.math.BigInteger

fun TvmContractSymbolicTestResult.toSarifReport(methodsMapping: Map<BigInteger, String>): String = SarifSchema210(
    schema = TsaSarifSchema.SCHEMA,
    version = TsaSarifSchema.VERSION,
    runs = listOf(
        Run(
            tool = TsaSarifSchema.TsaSarifTool.TOOL,
            results = testSuites.flatMap { it.toSarifResult(methodsMapping) }
        )
    )
).let { TvmContractCode.json.encodeToString(it) }

private fun TvmSymbolicTestSuite.toSarifResult(methodsMapping: Map<BigInteger, String>): List<Result> {
    val allErroneousTests = tests.filter { it.result is TvmMethodFailure }

    return allErroneousTests.map {
        val methodFailure = it.result as TvmMethodFailure

        val methodId = it.methodId
        val methodName = methodsMapping[methodId]

        Result(
            ruleID = resolveRuleId(methodFailure.failure),
            level = TsaSarifSchema.TsaSarifResult.LEVEL,
            message = Message(text = methodFailure.failure.toString()),
            locations = listOf(
                Location(
                    logicalLocations = listOf(
                        LogicalLocation(decoratedName = methodId.toString(), fullyQualifiedName = methodName)
                    ),
                )
            ),
            codeFlows = resolveCodeFlows(it.stackTrace, methodsMapping),
            properties = PropertyBag(
                mapOf(
                    "gasUsage" to it.gasUsage,
                    "usedParameters" to TvmContractCode.json.encodeToJsonElement(it.usedParameters),
                    "resultStack" to TvmContractCode.json.encodeToJsonElement(it.result.stack),
                )
            ),
        )
    }
}

private fun resolveRuleId(methodResult: TvmFailure): String = methodResult.exit.ruleName

private fun resolveCodeFlows(stackTrace: List<TvmInst>, methodsMapping: Map<BigInteger, String>): List<CodeFlow> {
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