package org.ton.examples.intercontract

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.ton.RUN_HARD_TESTS_REGEX
import org.ton.RUN_HARD_TESTS_VAR
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.exitCode
import org.ton.test.utils.extractBocContractFromResource
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractCommunicationSchemeFromResource
import org.ton.test.utils.extractConcreteDataFromResource
import org.ton.test.utils.getAddressBits
import org.usvm.machine.ExploreExitCodesStopStrategy
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CroutonfiNewTest {
    private val checkerPath = "/intercontract/croutonfi-new/checker.fc"
    private val schemePath = "/intercontract/croutonfi-new/scheme.json"
    private val vaultCodePath = "/intercontract/croutonfi-new/vault_code.boc"
    private val vaultDataPath = "/intercontract/croutonfi-new/vault_data.boc"
    private val poolCodePath = "/intercontract/croutonfi-new/pool_code.boc"
    private val poolDataPath = "/intercontract/croutonfi-new/pool_data.boc"

    @EnabledIfEnvironmentVariable(named = RUN_HARD_TESTS_VAR, matches = RUN_HARD_TESTS_REGEX)
    @Test
    fun findTonDrain() {
        val checkerContract = extractCheckerContractFromResource(checkerPath)
        val vaultContract = extractBocContractFromResource(vaultCodePath)
        val poolContract = extractBocContractFromResource(poolCodePath)
        val communicationScheme = extractCommunicationSchemeFromResource(schemePath)

        val options =
            TvmOptions(
                useReceiverInputs = false,
                intercontractOptions = IntercontractOptions(communicationScheme = communicationScheme),
                turnOnTLBParsingChecks = false,
                enableOutMessageAnalysis = true,
                stopOnFirstError = false,
                timeout = 3.minutes,
                solverTimeout = 3.seconds,
                performAdditionalChecksWhileResolving = true,
            )

        val concreteVaultData =
            TvmConcreteContractData(
                addressBits = getAddressBits("0:1d5fdacd17489f917240a3b097839bfbf3205b3fd3b52f850beccf442345cc92"),
                initialBalance = 11319590000.toBigInteger(),
                contractC4 = extractConcreteDataFromResource(vaultDataPath),
            )

        val concretePoolData =
            TvmConcreteContractData(
                addressBits = getAddressBits("0:7b3abba2d73fdd28e3681ee825be2d9b314a660f87f0d19e02da07b00f614fd0"),
                initialBalance = 32757070000L.toBigInteger(),
                contractC4 = extractConcreteDataFromResource(poolDataPath),
            )

        val tests =
            analyzeInterContract(
                listOf(checkerContract, vaultContract, poolContract),
                concreteContractData = listOf(TvmConcreteContractData(), concreteVaultData, concretePoolData),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
                additionalStopStrategy = ExploreExitCodesStopStrategy(setOf(1000)),
            )

        assert(tests.isNotEmpty())

        checkInvariants(
            tests,
            listOf { test ->
                test.exitCode() != 1000
            },
        )
    }
}
