package org.ton.examples.outmsg

import org.ton.cell.CellBuilder
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Ignore
import kotlin.test.Test

class OnOutMessageTest {
    private val checker = "/on-out-message/checker.fc"
    private val sender = "/on-out-message/sender.fc"

    @Ignore("malformed out message")
    @Test
    fun onOutMessageTest0() {
        onOutMessageBaseTest(0)
    }

    @Ignore("malformed out message")
    @Test
    fun onOutMessageTest1() {
        onOutMessageBaseTest(1)
    }

    @Ignore("malformed out message")
    @Test
    fun onOutMessageTest2() {
        onOutMessageBaseTest(2)
    }

    @Ignore("malformed out message")
    @Test
    fun onOutMessageTest3() {
        onOutMessageBaseTest(3)
    }

    @Ignore("malformed out message")
    @Test
    fun onOutMessageTest4() {
        onOutMessageBaseTest(4)
    }

    @Ignore("malformed out message")
    @Test
    fun onOutMessageTest5() {
        onOutMessageBaseTest(5)
    }

    private fun onOutMessageBaseTest(flag: Int) {
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

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode !in 200..706 },
        )

        propertiesFound(
            tests,
            listOf { test ->
                (test.result as? TvmTestFailure)?.exitCode == 10000
            },
        )
    }
}
