package org.ton.examples.outmsg

import org.junit.jupiter.api.Tag
import org.ton.cell.CellBuilder
import org.ton.test.utils.assertInvariantsHold
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.softFailure
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.state.TvmConstructedMessageCellOverflow
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Test

@Tag("intercontract")
class OnOutMessageTest {
    private val checker = "/on-out-message/checker.fc"
    private val sender = "/on-out-message/sender.fc"

    @Test
    fun onOutMessageTest0() {
        onOutMessageBaseTest(0)
    }

    @Test
    fun onOutMessageTest1() {
        onOutMessageBaseTest(1)
    }

    @Test
    fun onOutMessageTest2() {
        onOutMessageBaseTest(2)
    }

    @Test
    fun onOutMessageTest3() {
        onOutMessageBaseTest(3)
    }

    @Test
    fun onOutMessageTest4() {
        onOutMessageBaseTest(4)
    }

    @Test
    fun onOutMessageTest5() {
        onOutMessageBaseTest(5)
    }

    @Test
    fun onOutMessageTest6() {
        onOutMessageBaseTest(
            flag = 6,
            expectedSoftFailure = true,
        )
    }

    private fun onOutMessageBaseTest(
        flag: Int,
        expectedSoftFailure: Boolean = false,
    ) {
        val checkerContract = extractCheckerContractFromResource(checker)
        val analyzedSender = extractFuncContractFromResource(sender)

        val options =
            TvmOptions(
                turnOnTLBParsingChecks = false,
                enableOutMessageAnalysis = true,
                stopOnFirstError = false,
            )

        val tests =
            analyzeInterContract(
                listOf(checkerContract, analyzedSender),
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(contractC4 = CellBuilder.beginCell().storeInt(flag, 64).endCell()),
                        TvmConcreteContractData(),
                    ),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        if (!expectedSoftFailure) {
            tests.assertInvariantsHold(
                { test -> (test.result as? TvmTestFailure)?.exitCode !in 200..706 },
                { test -> test.eventsList.all { it.computePhaseResult is TvmSuccessfulExecution } },
            )

            tests.assertPropertiesFound(
                { test -> (test.result as? TvmTestFailure)?.exitCode == 10000 },
            )
        } else {
            tests.assertPropertiesFound(
                { test -> test.softFailure == TvmConstructedMessageCellOverflow },
            )
        }
    }
}
