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
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.test.resolver.exitCode
import org.usvm.test.resolver.toTvmCell
import kotlin.test.Test

class AuthorizationWhitelistTest {
    private val whitelistChecker = "/checkers/whitelist/checker.fc"

    private val contractWithWhitelistAuth = "/checkers/whitelist/whitelisted-contract.fc"

    @Test
    fun `checker playground test`() {
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
            { test -> (test.result.exitCode() == 1000) implies { test.resolvedEnumeratedValues[0]!!.size == 2 } },
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
                options =
                    TvmOptions(
                        useIntBlasting = false,
                    ),
            )
        tests.assertPropertiesFound(hasExitCode(1000))
        // the assertion is as such, because there are not-an-error exits that do not mean that we have
        // passed an authorization
        val someHasPassedAuth =
            tests.filter(hasExitCode(1000)).any {
                val fetchedOwners = it.resolvedEnumeratedValues[0]
                fetchedOwners != null && fetchedOwners.size == 2
            }

        assert(someHasPassedAuth)

        tests.filter(hasExitCode(1000)).assertInvariantHolds {
            val fetchedCode = it.resolvedEnumeratedValues[1000]
            (fetchedCode != null && fetchedCode.size == 1).implies {
                val canonicalFetched =
                    fetchedCode!!
                        .single()
                        .cell
                        .toTvmCell()
                        .toCell()
                canonicalFetched == walletCode
            }
        }
    }
}
