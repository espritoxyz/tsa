package org.ton.examples.checkers

import org.ton.bytecode.toCell
import org.ton.cell.CellBuilder
import org.ton.examples.intercontract.implies
import org.ton.test.utils.assertInvariantHolds
import org.ton.test.utils.assertInvariantsHold
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.exitCode
import org.ton.test.utils.extractBocContractFromResource
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractConcreteDataFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.hasExitCode
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.analyzeInterContract
import org.usvm.test.resolver.toTvmCell
import org.usvm.test.resolver.transformTestCellIntoCell
import kotlin.test.Test

const val INF = 5

class AuthCodeEnumerationTest {
    private val checker = "/checkers/auth/auth-code-enumeration-checker.fc"
    private val checkerWithOpcodes = "/checkers/auth/auth-code-enumeration-checker-with-opcode.fc"

    private val contractHashCheckWithFixedData = "/checkers/auth/contract-with-hash-check-fixed-data-auth.fc"

    @Test
    fun `hash-based auth with fixed data`() {
        val checker = extractCheckerContractFromResource(checker)
        val contract = extractFuncContractFromResource(contractHashCheckWithFixedData)
        val tests = analyzeInterContract(contracts = listOf(checker, contract))
        tests.assertPropertiesFound(hasExitCode(1000))
        tests.filter(hasExitCode(1000)).assertInvariantsHold(
            {
                (it.exitCode() == 1000) implies {
                    val fetched = it.resolvedEnumeratedValues[1000]
                    fetched != null && fetched.size == 1
                }
            },
        )
    }

    private val authHashCheckWithNonFixedData = "/checkers/auth/contract-with-hash-check-non-fixed-data.fc"

    @Test
    fun `hash-based auth with not fixed data`() {
        val checker = extractCheckerContractFromResource(checker)
        val contract = extractFuncContractFromResource(authHashCheckWithNonFixedData)
        val tests = analyzeInterContract(contracts = listOf(checker, contract))
        tests.assertPropertiesFound(hasExitCode(1000))
        tests.filter(hasExitCode(1000)).assertInvariantsHold(
            {
                val fetchedCode = it.resolvedEnumeratedValues[1000]
                fetchedCode != null && fetchedCode.size == 1
            },
        )
    }

    private val authSliceBitsComparison =
        "/checkers/auth/contract-with-hash-check-non-fixed-data-slice-bits-comparison.fc"

    @Test
    fun `hash-based auth with not fixed data with slice bits comparison`() {
        val checker = extractCheckerContractFromResource(checker)
        val contract =
            extractFuncContractFromResource(
                authSliceBitsComparison,
            )
        val tests = analyzeInterContract(contracts = listOf(checker, contract))
        tests.assertPropertiesFound(hasExitCode(1000))
        tests.filter(hasExitCode(1000)).assertInvariantsHold(
            {
                val fetchedCode = it.resolvedEnumeratedValues[1000]
                fetchedCode != null && fetchedCode.size == 1
            },
        )
    }

    private val authWithCodeFromC4 = "/checkers/auth/contract-with-hash-check-non-fixed-data-custom-code.fc"

    @Test
    fun `hash-based auth with not fixed data with checking the code`() {
        val checker = extractCheckerContractFromResource(checker)
        val contract =
            extractFuncContractFromResource(authWithCodeFromC4)
        val contractData = CellBuilder().storeRef(CellBuilder().storeUInt(137, 64).endCell()).endCell()
        val tests =
            analyzeInterContract(
                contracts = listOf(checker, contract),
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(),
                        TvmConcreteContractData(contractC4 = contractData),
                    ),
            )
        tests.assertPropertiesFound(hasExitCode(1000))
        tests.assertInvariantsHold(
            {
                if (it.exitCode() == 1000) {
                    val fetched = it.resolvedEnumeratedValues[1000]
                    assert(fetched != null && fetched.size == 1)
                    val value = fetched!!.single().cell
                    val cellValue = transformTestCellIntoCell(value)
                    cellValue == contractData.refs.single()
                } else {
                    true
                }
            },
        )
    }

    private val noAuthContract = "/checkers/auth/contract-no-auth.fc"

    @Test
    fun `no auth`() {
        val checker = extractCheckerContractFromResource(checker)
        val contract = extractFuncContractFromResource(noAuthContract)
        val tests = analyzeInterContract(contracts = listOf(checker, contract))
        tests.assertPropertiesFound(hasExitCode(1000))
        tests.assertInvariantsHold(
            {
                (it.exitCode() == 1000) implies {
                    val fetched = it.resolvedEnumeratedValues[1000]
                    fetched != null && fetched.size > INF
                }
            },
        )
    }

    private fun CellBuilder.storeCoin(
        value: Int,
        nanogramsWidthDivBy8: Int,
    ): CellBuilder = storeUInt(nanogramsWidthDivBy8, 4).storeUInt(value, 8 * nanogramsWidthDivBy8)

    private fun CellBuilder.storeStdAddr(accountId: Int): CellBuilder =
        storeUInt(4, 3) // 0b10 from std + 0b0 from anycast
            .storeUInt(0, 8)
            .storeUInt(accountId, 256)

    private val internalTransferOpcode = 0x178d4519

    /**
     * slightly modified version of `jetton-wallet.func` from the same folder
     */
    private val modernJettonOnlyCodeAuth =
        "/contracts/modern-jetton/jetton-wallet-internal-transfer-only-code-auth.func"

    @Test
    fun `jetton authorization`() {
        val checker = extractCheckerContractFromResource(checkerWithOpcodes)
        val contract = extractCheckerContractFromResource(modernJettonOnlyCodeAuth)
        val walletCode = CellBuilder().storeUInt(13, 64).storeRef(CellBuilder().storeUInt(3, 5).endCell()).endCell()
        val contractData =
            CellBuilder()
                .storeCoin(1000_000, 4)
                .storeStdAddr(accountId = 0)
                .storeStdAddr(accountId = 1)
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
        // the assertion is as such, because there are not-an-error exits that do not mean that we have
        // passed an authorization
        val someHasPassedAuth =
            tests.filter(hasExitCode(1000)).any {
                val fetchedCode = it.resolvedEnumeratedValues[1000]
                fetchedCode != null && fetchedCode.size == 1
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

    @Test
    fun `some real authorization`() {
        val checker = extractCheckerContractFromResource(checkerWithOpcodes)
        val contract = extractBocContractFromResource("/checkers/auth/some-jetton-wallet-code.boc")
        val data = extractConcreteDataFromResource("/checkers/auth/some-jetton-wallet-data.boc")
        val checkerC4 = CellBuilder().storeUInt(internalTransferOpcode, 32).endCell()
        val tests =
            analyzeInterContract(
                contracts = listOf(checker, contract),
                concreteContractData =
                    listOf(
                        TvmConcreteContractData(contractC4 = checkerC4),
                        TvmConcreteContractData(contractC4 = data),
                    ),
            )
        tests.assertPropertiesFound(hasExitCode(1000))
        // the assertion is as such, because there are not-an-error exits that do not mean that we have
        // passed an authorization
        val someHasPassedAuth =
            tests.filter(hasExitCode(1000)).any {
                val fetchedCode = it.resolvedEnumeratedValues[1000]
                fetchedCode != null && fetchedCode.size == 1
            }
        assert(someHasPassedAuth)
    }
}
