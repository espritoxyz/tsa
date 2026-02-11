package org.ton.examples.checkers

import org.ton.test.utils.FIFT_STDLIB_RESOURCE
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.exitCode
import org.ton.test.utils.extractResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.ExploreExitCodesStopStrategy
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.test.resolver.TvmSymbolicTestSuite
import kotlin.test.Test

class ReplayAttackCheckerTest {
    private val checkerPath = "/checkers/replay/checker.fc"
    private val highloadWalletV3 = "/contracts/highload-wallet-v3/highload-wallet-v3.fc"
    private val highloadWalletVulnerable = "/checkers/replay/vulnerable-highload-1/highload-wallet-v3.fc"
    private val highloadWalletVulnerable2 = "/checkers/replay/vulnerable-highload-2/highload-wallet-v3.fc"

    private fun runTest(contractPath: String): TvmSymbolicTestSuite {
        val contractPath = extractResource(contractPath)
        val checkerPath = extractResource(checkerPath)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true,
            )
        val analyzedContract = getFuncContract(contractPath, FIFT_STDLIB_RESOURCE)

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
            additionalStopStrategy = ExploreExitCodesStopStrategy(setOf(1000)),
        )
    }

    @Test
    fun testWalletHighload() {
        val tests = runTest(highloadWalletV3)

        checkInvariants(
            tests,
            listOf { test -> test.exitCode() != 1000 },
        )
    }

    @Test
    fun testWalletHighloadVulnerable() {
        val tests = runTest(highloadWalletVulnerable)

        propertiesFound(
            tests,
            listOf { test -> test.exitCode() == 1000 },
        )
    }

    @Test
    fun testWalletHighloadVulnerable2() {
        val tests = runTest(highloadWalletVulnerable2)

        propertiesFound(
            tests,
            listOf { test -> test.exitCode() == 1000 },
        )
    }
}
