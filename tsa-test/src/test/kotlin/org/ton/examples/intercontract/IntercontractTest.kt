package org.ton.examples.intercontract

import org.ton.communicationSchemeFromJson
import org.ton.test.utils.analyzeFuncIntercontract
import org.ton.test.utils.extractResource
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmOptions
import org.usvm.test.resolver.TvmMethodFailure
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class IntercontractTest {
    private val rootPath: String = "/intercontract/root.fc"
    private val contract1Path: String = "/intercontract/contract-1.fc"
    private val contract2Path: String = "/intercontract/contract-2.fc"
    private val schemePath: String = "/intercontract/sample-intercontract-scheme.json"

    @Test
    fun testIntercontract() {
        val sources = listOf(
            extractResource(rootPath),
            extractResource(contract1Path),
            extractResource(contract2Path)
        )

        val schemeJson = extractResource(schemePath).readText()
        val scheme = communicationSchemeFromJson(schemeJson)
        val options = TvmOptions(intercontractOptions = IntercontractOptions(scheme), enableOutMessageAnalysis = true)

        val resultStates = analyzeFuncIntercontract(
            sources = sources,
            options = options,
            startContract = 0,
        )
        val failedPaths = resultStates.mapNotNull { test ->
            val result = test.result as? TvmMethodFailure
                ?: return@mapNotNull null

            result.exitCode to test.intercontractPath
        }

        val invalidatedInvariantCode = 999

        // no invalidated invariants
        val invalidatedInvariantCount = failedPaths.count { it.first == invalidatedInvariantCode }
        assertEquals(0, invalidatedInvariantCount)

        // simple path test
        val simplePath = listOf(0, 1, 2)
        val simplePathEndCode = 101
        assertContains(failedPaths, simplePathEndCode to simplePath)

        // complex path test
        val complexPath = listOf(0, 2, 1, 2, 2)
        val complexPathEndCode = 102
        assertContains(failedPaths, complexPathEndCode to complexPath)
    }
}