package org.ton.examples.checkers

import org.ton.TvmContractHandlers
import org.ton.bitstring.BitString
import org.ton.bytecode.TsaContractCode
import org.ton.cell.Cell
import org.ton.communicationSchemeFromJson
import org.ton.test.utils.FIFT_STDLIB_RESOURCE
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TactSourcesDescription
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.machine.getTactContract
import org.usvm.machine.state.ContractId
import org.usvm.test.resolver.TvmExecutionWithSoftFailure
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmMethodSymbolicResult
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmTestInput
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CheckersTest {
    private val checker = "/fees/forward/checker.fc"
    private val sender = "/fees/forward/sender.fc"
    private val receiver = "/fees/forward/receiver.fc"
    private val scheme = "/fees/forward/inter-contract.json"

    @Ignore("Proper calculation is not supported yet")
    @Test
    fun bounceTest() {
        val checkerContract = extractCheckerContractFromResource(checker)
        val analyzedSender = extractFuncContractFromResource(sender)
        val analyzedReceiver = extractFuncContractFromResource(receiver)
        val communicationScheme = extractCommunicationSchemeFromResource(scheme)
        val options = createIntercontractOptions(scheme)

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedSender, analyzedReceiver),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        propertiesFound(
            tests,
            listOf { test ->
                val checkerExitCode = test.eventsList.single { it.id == 0 }.methodResult
                val checkerExitCodeGood = (checkerExitCode as? TvmMethodFailure)?.exitCode == 10000
                checkerExitCodeGood 
            },
        )

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmMethodFailure)?.exitCode != 228 },
        )
    }
}
