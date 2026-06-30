package org.ton.examples.checkers

import org.ton.bytecode.toCell
import org.ton.cell.CellBuilder
import org.ton.examples.intercontract.implies
import org.ton.test.utils.assertInvariantHolds
import org.ton.test.utils.assertInvariantsHold
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.hasExitCode
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.analyzeInterContract
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmTestAuthValue
import org.usvm.test.resolver.TvmTestIntegerValue
import org.usvm.test.resolver.exitCode
import org.usvm.test.resolver.toTvmCell
import kotlin.test.Test

class AuthorizationWhitelistTest {
    private val whitelistChecker = "/checkers/whitelist/checker.fc"

    private val contractWithWhitelistAuth = "/checkers/whitelist/whitelisted-contract.fc"

    @Test
    fun `simple whitelist authorization`() {
        val checker = extractCheckerContractFromResource(whitelistChecker)
        val contract = extractFuncContractFromResource(contractWithWhitelistAuth)
        val checkerData = CellBuilder().storeUInt(0, 32).endCell()
        val tests =
            analyzeInterContract(
                listOf(checker, contract),
                concreteContractData = listOf(TvmConcreteContractData(checkerData), TvmConcreteContractData()),
            )
        tests.assertPropertiesFound(
            { test -> test.result.exitCode() == 1000 },
        )

        tests.assertInvariantsHold(
            { test -> (test.result.exitCode() == 1000) implies { test.authorizedOwners().size == 2 } },
        )
    }

    private val modernJettonWallet = "/contracts/modern-jetton/jetton-wallet.func"
    private val internalTransferOpcode = 0x178d4519

    @Test
    fun `jetton authorization`() {
        val checker = extractCheckerContractFromResource(whitelistChecker)
        val contract = extractCheckerContractFromResource(modernJettonWallet)
        val walletCode = CellBuilder().storeUInt(13, 64).storeRef(CellBuilder().storeUInt(3, 5).endCell()).endCell()
        val contractData =
            CellBuilder()
                .storeCoin(1000_000, 4)
                .storeStdAddr(accountId = 15) // owner
                .storeStdAddr(accountId = 255) // master
                .storeRef(walletCode)
                .endCell()
        val checkerC4 = CellBuilder().storeUInt(internalTransferOpcode, 32).endCell()
        val tests =
            analyzeInterContract(
                contracts = listOf(checker, contract),
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(contractC4 = checkerC4),
                        TvmConcreteContractData(contractC4 = contractData),
                    ),
            )
        tests.assertPropertiesFound(hasExitCode(1000))
        tests.filter(hasExitCode(1000)).assertInvariantHolds {
            val authorizedCodes = it.authorizedCodes()
            val authorizedOwners = it.authorizedOwners()
            val fetchedCode = authorizedCodes.single().toTvmCell().toCell()
            val fetchedOwner = authorizedOwners.single().value
            fetchedCode == walletCode && fetchedOwner == 255.toBigInteger()
        }
    }
}

fun TvmSymbolicTest.authorizedOwners(): List<TvmTestIntegerValue> =
    resolvedAuthValues.filterIsInstance<TvmTestAuthValue.AuthorizedOwner>().map { it.accountId }
