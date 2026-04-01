package org.ton.examples.intercontract

import org.ton.bytecode.TsaContractCode
import org.ton.cell.CellBuilder
import org.ton.test.utils.assertInvariantsHold
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.doesNotEndWithExitCode
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.hasExitCode
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.test.resolver.TvmSymbolicTestSuite
import kotlin.test.Test
import kotlin.test.assertTrue

class ReserveTest {
    object Mode0NormalMessage {
        val checker = "/intercontract/reserve/mode-0-normal-send/checker.fc"
        val sender = "/intercontract/reserve/mode-0-normal-send/sender.fc"
    }

    object Mode0SendRemainingValue {
        val checker = "/intercontract/reserve/mode-0-remaining-value/checker.fc"
        val sender = "/intercontract/reserve/mode-0-remaining-value/sender.fc"
    }

    @Test
    fun `reserve 0 - normal message - suffices`() {
        val checker = extractCheckerContractFromResource(Mode0NormalMessage.checker)
        val sender = extractFuncContractFromResource(Mode0NormalMessage.sender)
        val contractC4 =
            CellBuilder()
                .storeInt(2_000_000_000, 64) // initial balance
                .storeInt(1_000_000_000, 64) // to reserve
                .storeInt(500_000_000, 64) // to send
                .endCell()
        val checkerData = TvmConcreteContractData(contractC4)
        val tests = runAnalysis(checker, sender, checkerData)
        tests.assertPropertiesFound(hasExitCode(10000))
    }

    @Test
    fun `reserve 0 - normal message - insufficient funds`() {
        val checker = extractCheckerContractFromResource(Mode0NormalMessage.checker)
        val sender = extractFuncContractFromResource(Mode0NormalMessage.sender)
        val contractC4 =
            CellBuilder()
                .storeInt(20_000_000_000, 64) // initial balance
                .storeInt(15_000_000_000, 64) // to reserve
                .storeInt(10_000_000_000, 64) // to send
                .endCell()
        val checkerData = TvmConcreteContractData(contractC4)
        val tests = runAnalysis(checker, sender, checkerData)
        tests.assertInvariantsHold(doesNotEndWithExitCode(10000))
        assertTrue(tests.isNotEmpty())
    }

    @Test
    fun `reserve 0 - send remaining balance - true bound`() {
        val checker = extractCheckerContractFromResource(Mode0SendRemainingValue.checker)
        val sender = extractFuncContractFromResource(Mode0SendRemainingValue.sender)
        val contractC4 =
            CellBuilder()
                .storeInt(20_000_000_000, 64) // initial balance
                .storeInt(15_000_000_000, 64) // to reserve
                .storeInt(8_000_000_000, 64) // expected upper bound on out message
                .endCell()
        val checkerData = TvmConcreteContractData(contractC4)
        val tests = runAnalysis(checker, sender, checkerData)
        tests.assertInvariantsHold(doesNotEndWithExitCode(400))
        tests.assertPropertiesFound(hasExitCode(10000))
    }

    @Test
    fun `reserve 0 - send remaining balance - bound too small`() {
        val checker = extractCheckerContractFromResource(Mode0SendRemainingValue.checker)
        val sender = extractFuncContractFromResource(Mode0SendRemainingValue.sender)
        val contractC4 =
            CellBuilder()
                .storeInt(20_000_000_000, 64) // initial balance
                .storeInt(15_000_000_000, 64) // to reserve
                .storeInt(1_000_000_000, 64) // expected upper bound on out message
                .endCell()
        val checkerData = TvmConcreteContractData(contractC4)
        val tests = runAnalysis(checker, sender, checkerData)
        tests.assertPropertiesFound(hasExitCode(400))
    }

    private fun runAnalysis(
        checker: TsaContractCode,
        sender: TsaContractCode,
        checkerData: TvmConcreteContractData,
    ): TvmSymbolicTestSuite =
        analyzeInterContract(
            listOf(checker, sender),
            startContractId = 0,
            methodId = TvmContext.RECEIVE_INTERNAL_ID,
            options = TvmOptions(stopOnFirstError = true, enableOutMessageAnalysis = true),
            concreteContractData =
                listOf(
                    checkerData,
                    TvmConcreteContractData(),
                ),
        )
}
