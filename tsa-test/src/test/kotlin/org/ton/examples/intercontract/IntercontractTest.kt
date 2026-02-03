package org.ton.examples.intercontract

import org.junit.jupiter.api.Tag
import org.ton.communicationSchemeFromJson
import org.ton.test.utils.FIFT_STDLIB_RESOURCE
import org.ton.test.utils.analyzeFuncIntercontract
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.getAddressBits
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.test.resolver.TvmTestFailure
import org.usvm.test.resolver.TvmTestInput
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("intercontract")
class IntercontractTest {
    private val rootPath: String = "/intercontract/sample/root.fc"
    private val contract1Path: String = "/intercontract/sample/contract-1.fc"
    private val contract2Path: String = "/intercontract/sample/contract-2.fc"
    private val schemePath: String = "/intercontract/sample/sample-intercontract-scheme.json"

    private val intercontractConsistencySender = "/intercontract/consistency/inter-contract-consistency.fc"
    private val intercontractConsistencyRecipient = "/intercontract/consistency/inter-contract-consistency-recipient.fc"
    private val intercontractConsistencyChecker = "/intercontract/consistency/inter-contract-consistency-checker.fc"
    private val intercontractConsistencyScheme = "/intercontract/consistency/inter-contract-consistency.json"

    @Test
    fun testIntercontractSample() {
        val sources =
            listOf(
                extractResource(rootPath),
                extractResource(contract1Path),
                extractResource(contract2Path),
            )

        val schemeJson = extractResource(schemePath).readText()
        val scheme = communicationSchemeFromJson(schemeJson)
        val options =
            TvmOptions(
                intercontractOptions = IntercontractOptions(scheme),
                enableOutMessageAnalysis = true,
            )

        val resultStates =
            analyzeFuncIntercontract(
                sources = sources,
                options = options,
                startContract = 0,
            )
        val failedPaths =
            resultStates.mapNotNull { test ->
                val result =
                    test.result as? TvmTestFailure
                        ?: return@mapNotNull null

                result.exitCode to test.intercontractPath
            }

        val invalidatedInvariantCode = 999

        // no invalidated invariants
        val invalidatedInvariantCount = failedPaths.count { it.first == invalidatedInvariantCode }
        assertEquals(0, invalidatedInvariantCount)

        // simple path testa
        // note: these checks depend on the order we take the messages from a queue, which is an implementation detail
        val simplePath = listOf(0, 1, 2)
        val simplePathEndCode = 101
        assertContains(failedPaths, simplePathEndCode to simplePath)

        // complex path test
        val complexPath = listOf(0, 2, 1, 2, 2)
        val complexPathEndCode = 102
        assertContains(failedPaths, complexPathEndCode to complexPath)
    }

    @Test
    fun intercontractConsistencyTest() {
        val pathSender = extractResource(intercontractConsistencySender)
        val pathRecipient = extractResource(intercontractConsistencyRecipient)
        val checkerPath = extractResource(intercontractConsistencyChecker)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true,
            )
        val analyzedSender = getFuncContract(pathSender, FIFT_STDLIB_RESOURCE)
        val analyzedRecipient = getFuncContract(pathRecipient, FIFT_STDLIB_RESOURCE)

        val communicationSchemePath = extractResource(intercontractConsistencyScheme)
        val communicationScheme = communicationSchemeFromJson(communicationSchemePath.readText())

        val options =
            TvmOptions(
                intercontractOptions =
                    IntercontractOptions(
                        communicationScheme = communicationScheme,
                    ),
                enableOutMessageAnalysis = true,
                turnOnTLBParsingChecks = false,
            )

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedSender, analyzedRecipient),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(),
                        TvmConcreteContractData(),
                        TvmConcreteContractData(
                            addressBits =
                                getAddressBits(
                                    "0:fd38d098511c43015e02cd185cfcac3befffa89a2a7f20d65440638a9475b9db",
                                ),
                        ),
                    ),
            )

        assertTrue { tests.isNotEmpty() }

        checkInvariants(
            tests,
            listOf(
                { test -> (test.result as? TvmTestFailure)?.exitCode == 257 },
                { test ->
                    ((((test.additionalInputs[0] as? TvmTestInput.RecvInternalInput)?.msgBody)?.cell)?.data)
                        ?.startsWith(
                            "00000000000000000000000001100100" +
                                getAddressBits(
                                    "0:fd38d098511c43015e02cd185cfcac3befffa89a2a7f20d65440638a9475b9db",
                                ),
                        ) == true
                },
            ),
        )
    }
}
