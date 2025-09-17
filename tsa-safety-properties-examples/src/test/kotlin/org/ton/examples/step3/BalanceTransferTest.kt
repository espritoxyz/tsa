package org.ton.examples.step3

import FIFT_STDLIB_RESOURCE
import org.ton.communicationSchemeFromJson
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.machine.getResourcePath
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmTestIntegerValue
import org.usvm.test.resolver.TvmTestSliceValue
import kotlin.io.path.readText
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class BalanceTransferTest {
    private val walletContractResourcePath = "/examples/step3/wallet.fc"
    private val checkerResourcePath = "/examples/step3/balance_transfer_checker.fc"
    private val intercontractSchemePath = "/examples/step3/wallet-intercontract-scheme.json"

    @Ignore
    @Test
    fun testBalanceTransfer() {
        val walletContractPath = getResourcePath<BalanceTransferTest>(walletContractResourcePath)
        val walletContractCode = getFuncContract(walletContractPath, FIFT_STDLIB_RESOURCE)

        val checkerPath = getResourcePath<BalanceTransferTest>(checkerResourcePath)
        val checkerCode = getFuncContract(checkerPath, FIFT_STDLIB_RESOURCE, isTSAChecker = true)

        val communicationSchemePath = getResourcePath<BalanceTransferTest>(intercontractSchemePath)
        val communicationScheme = communicationSchemeFromJson(communicationSchemePath.readText())
        val options =
            TvmOptions(
                intercontractOptions = IntercontractOptions(communicationScheme),
                turnOnTLBParsingChecks = false,
                enableOutMessageAnalysis = true,
            )

        // Count wallet contract twice to distinguish between different accounts
        val contracts = listOf(checkerCode, walletContractCode, walletContractCode)
        val result =
            analyzeInterContract(
                contracts,
                startContractId = 0, // Checker contract is the first to analyze
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )
        val failures = result.tests.filter { it.result is TvmMethodFailure }
        val nonTransferredBalanceExecution = failures.single { (it.result as TvmMethodFailure).exitCode == 257 }

        val transferredValue = nonTransferredBalanceExecution.fetchedValues[-1] as TvmTestIntegerValue

        val secondInitialBalance = nonTransferredBalanceExecution.fetchedValues[2] as TvmTestIntegerValue
        val secondNewBalance = nonTransferredBalanceExecution.fetchedValues[22] as TvmTestIntegerValue

        // Check that the second balance was not increased ...
        assertEquals(secondInitialBalance, secondNewBalance)

        val firstInitialBalance = nonTransferredBalanceExecution.fetchedValues[1] as TvmTestIntegerValue
        val firstNewBalance = nonTransferredBalanceExecution.fetchedValues[11] as TvmTestIntegerValue

        // ... but the first balance was decreased
        assertEquals(firstInitialBalance.value, firstNewBalance.value.add(transferredValue.value))

        // Check it is possible only when the target address equals to the wallet address
        val targetAddress = nonTransferredBalanceExecution.fetchedValues[0] as TvmTestSliceValue
        val walletAddress = nonTransferredBalanceExecution.contractAddress
        assertEquals(targetAddress.cell.data.substring(targetAddress.dataPos), walletAddress.data)
    }
}
