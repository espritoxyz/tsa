package org.ton.examples.checkers

import io.ktor.util.extension
import org.ton.cell.CellBuilder
import org.ton.test.utils.FIFT_STDLIB_RESOURCE
import org.ton.test.utils.assertInvariantsHold
import org.ton.test.utils.assertNotEmpty
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.doesNotEndWithExitCode
import org.ton.test.utils.exitCode
import org.ton.test.utils.extractResource
import org.ton.test.utils.hasExitCode
import org.ton.test.utils.propertiesFound
import org.usvm.machine.ExploreExitCodesStopStrategy
import org.usvm.machine.FiftAnalyzer
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.test.resolver.TvmSymbolicTestSuite
import kotlin.test.Test

private const val REPLAY_FOUND = 1000

class ReplayAttackCheckerTest {
    private val checkerPath = "/checkers/replay/checker.fc"
    private val trivialVulnerableContract = "/checkers/replay/simple-vulnerable-replay.fc"
    private val trivialInvulnerableContract = "/checkers/replay/simple-invulnerable-replay.fc"
    private val highloadWalletV3 = "/contracts/highload-wallet-v3/highload-wallet-v3.fc"
    private val highloadWalletVulnerable = "/checkers/replay/vulnerable-highload-1/highload-wallet-v3.fc"
    private val highloadWalletVulnerable2 = "/checkers/replay/vulnerable-highload-2/highload-wallet-v3.fc"

    private val tutorialInit = "/checkers/replay/tutorial-init.fif" // TODO make first-class tolk tests
    private val tutorialIntermediate = "/checkers/replay/tutorial-partial-fix.fif"
    private val tutorialFull = "/checkers/replay/tutorial-full-fix.fif"

    /**
     * `python -c "import binascii; print(binascii.crc_hqx(b'getSeqno', 0) | 0x10000)"`
     */
    val seqnoMethodId = 100099

    private fun runTest(
        contractPath: String,
        seqnoRestriction: Int? = null,
        methodId: Int? = null,
    ): TvmSymbolicTestSuite {
        val contractPath = extractResource(contractPath)
        val checkerPath = extractResource(checkerPath)

        val checkerContract =
            getFuncContract(
                checkerPath,
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true,
            )
        val analyzedContract =
            when (val extension = contractPath.extension) {
                "fc" -> {
                    getFuncContract(contractPath, FIFT_STDLIB_RESOURCE)
                }

                "fif" -> {
                    FiftAnalyzer(FIFT_STDLIB_RESOURCE).convertToTvmContractCode(contractPath)
                }

                else -> {
                    error("unsupported file extension: $extension")
                }
            }

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
            additionalStopStrategy = ExploreExitCodesStopStrategy(setOf(REPLAY_FOUND)),
            concreteContractData =
                listOf(
                    TvmConcreteContractData(
                        contractC4 =
                            if (seqnoRestriction == null) {
                                CellBuilder().storeUInt(0, 1).endCell()
                            } else {
                                CellBuilder()
                                    .storeUInt(1, 1)
                                    .storeUInt(methodId ?: 37, 32)
                                    .storeUInt(seqnoRestriction, 255)
                                    .endCell()
                            },
                    ),
                    TvmConcreteContractData(),
                ),
        )
    }

    @Test
    fun `tutorial init`() {
        val tests = runTest(tutorialInit)
        tests.assertPropertiesFound(
            hasExitCode(REPLAY_FOUND),
        )
    }

    @Test
    fun `tutorial intermediate`() {
        val tests = runTest(tutorialIntermediate)
        tests.assertPropertiesFound(
            hasExitCode(REPLAY_FOUND),
        )
    }

    @Test
    fun `tutorial intermediate with partial fix`() {
        val tests = runTest(tutorialIntermediate, 20, seqnoMethodId)
        tests.assertPropertiesFound(
            hasExitCode(REPLAY_FOUND),
        )
    }

    @Test
    fun `tutorial intermediate with full fix`() {
        val tests = runTest(tutorialFull, 20, seqnoMethodId)
        tests.assertInvariantsHold(
            doesNotEndWithExitCode(REPLAY_FOUND),
        )
    }

    @Test
    fun `simple vulnerable`() {
        val tests = runTest(trivialVulnerableContract)
        tests.assertPropertiesFound(
            hasExitCode(REPLAY_FOUND),
        )
    }

    @Test
    fun `simple invulnerable`() {
        val tests = runTest(trivialInvulnerableContract, seqnoRestriction = 20)
        tests.assertNotEmpty()
        tests.assertInvariantsHold(
            doesNotEndWithExitCode(11),
            doesNotEndWithExitCode(REPLAY_FOUND),
        )
    }

    @Test
    fun testWalletHighload() {
        val tests = runTest(highloadWalletV3)

        checkInvariants(
            tests,
            listOf { test -> test.exitCode() != REPLAY_FOUND },
        )
    }

    @Test
    fun testWalletHighloadVulnerable() {
        val tests = runTest(highloadWalletVulnerable)

        propertiesFound(
            tests,
            listOf { test -> test.exitCode() == REPLAY_FOUND },
        )
    }

    @Test
    fun testWalletHighloadVulnerable2() {
        val tests = runTest(highloadWalletVulnerable2)

        propertiesFound(
            tests,
            listOf { test -> test.exitCode() == REPLAY_FOUND },
        )
    }
}
