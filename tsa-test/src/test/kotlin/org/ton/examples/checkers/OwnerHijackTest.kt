package org.ton.examples.checkers

import org.ton.cell.CellBuilder
import org.ton.test.utils.assertInvariantsHold
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.exitCode
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.test.resolver.TvmSymbolicTestSuite
import kotlin.test.Test

class OwnerHijackTest {
    private val ownerHijackSymbolicChecker = "/checkers/owner-hijack/owner-hijack-check-symbolic.fc"
    private val ownerHijackConcreteChecker = "/checkers/owner-hijack/owner-hijack-check-concrete.fc"
    private val vulnerableContract = "/checkers/owner-hijack/simple-vulnerable-contract.fc"
    private val invulnerableContractTrivial = "/checkers/owner-hijack/invulnerable-contract-trivial.fc"
    private val invulnerableContractWithOwner = "/checkers/owner-hijack/invulnerable-contract-with-owner.fc"

    private fun runSymbolicTest(contractPath: String): TvmSymbolicTestSuite {
        val checkerContract = extractCheckerContractFromResource(ownerHijackSymbolicChecker)
        val analyzedContract = extractFuncContractFromResource(contractPath)

        val getOwnerMethodId = 37
        val checkerCell = CellBuilder().storeInt(getOwnerMethodId, 32).endCell()
        val options =
            TvmOptions(
                stopOnFirstError = false,
                enableOutMessageAnalysis = true,
            )

        return analyzeInterContract(
            listOf(checkerContract, analyzedContract),
            startContractId = 0,
            methodId = TvmContext.RECEIVE_INTERNAL_ID,
            options = options,
            concreteContractData =
                listOf(
                    TvmConcreteContractData(contractC4 = checkerCell),
                    TvmConcreteContractData(),
                ),
        )
    }

    private fun runConcreteTest(contractPath: String): TvmSymbolicTestSuite {
        val checkerContract = extractCheckerContractFromResource(ownerHijackConcreteChecker)
        val analyzedContract = extractFuncContractFromResource(contractPath)
        val getOwnerMethodId = 37
        val sampleAddress = "10" + "0" + "0".repeat(8) + "0".repeat(255) + "1"
        val checkerCell =
            CellBuilder()
                .storeInt(getOwnerMethodId, 32)
                .storeBits(sampleAddress.map { it == '1' })
                .storeUInt(4, 4)
                .storeUInt(500_000_000, 4 * 8)
                .endCell()
        val options =
            TvmOptions(
                stopOnFirstError = false,
                enableOutMessageAnalysis = true,
            )

        return analyzeInterContract(
            listOf(checkerContract, analyzedContract),
            startContractId = 0,
            methodId = TvmContext.RECEIVE_INTERNAL_ID,
            options = options,
            concreteContractData =
                listOf(
                    TvmConcreteContractData(contractC4 = checkerCell),
                    TvmConcreteContractData(),
                ),
        )
    }

    @Test
    fun testVulnerable() {
        val tests = runSymbolicTest(vulnerableContract)
        tests.assertPropertiesFound(
            { test -> test.exitCode() == 1000 },
        )
    }

    @Test
    fun testVulnerableConcrete() {
        val tests = runConcreteTest(vulnerableContract)
        tests.assertPropertiesFound(
            { test -> test.exitCode() == 1000 },
        )
    }

    @Test
    fun testInVulnerableTrivial() {
        val tests = runSymbolicTest(invulnerableContractTrivial)
        tests.assertInvariantsHold(
            { test -> test.exitCode() != 1000 },
        )
    }

    @Test
    fun testInVulnerableWithOwner() {
        val tests = runSymbolicTest(invulnerableContractWithOwner)
        tests.assertInvariantsHold(
            { test -> test.exitCode() != 500 },
        )
    }
}
