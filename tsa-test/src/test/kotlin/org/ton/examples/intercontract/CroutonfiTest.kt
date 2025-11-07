package org.ton.examples.intercontract

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.ton.RUN_HARD_TESTS_REGEX
import org.ton.RUN_HARD_TESTS_VAR
import org.ton.test.utils.extractBocContractFromResource
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractCommunicationSchemeFromResource
import org.ton.test.utils.extractConcreteDataFromResource
import org.ton.test.utils.getAddressBits
import org.ton.test.utils.propertiesFound
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.test.resolver.TvmMethodFailure
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class CroutonfiTest {
    private val checkerPath = "/intercontract/croutonfi/checker.fc"
    private val schemePath = "/intercontract/croutonfi/scheme.json"
    private val vaultCodePath = "/intercontract/croutonfi/vault_code.boc"
    private val vaultDataPath = "/intercontract/croutonfi/vault_data.boc"
    private val poolCodePath = "/intercontract/croutonfi/pool_code.boc"
    private val poolDataPath = "/intercontract/croutonfi/pool_data.boc"

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
                timeout = 2.minutes,
            )

        val concreteVaultData =
            TvmConcreteContractData(
                addressBits = getAddressBits("0:1d5fdacd17489f917240a3b097839bfbf3205b3fd3b52f850beccf442345cc92"),
                initialBalance = 2148977707770L.toBigInteger(),
                contractC4 = extractConcreteDataFromResource(vaultDataPath),
            )

        val concretePoolData =
            TvmConcreteContractData(
                addressBits = getAddressBits("0:7b3abba2d73fdd28e3681ee825be2d9b314a660f87f0d19e02da07b00f614fd0"),
                initialBalance = 32106961941L.toBigInteger(),
                contractC4 = extractConcreteDataFromResource(poolDataPath),
            )

        val tests =
            analyzeInterContract(
                listOf(checkerContract, vaultContract, poolContract),
                concreteContractData = listOf(TvmConcreteContractData(), concreteVaultData, concretePoolData),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        propertiesFound(
            tests,
            listOf { test ->
                (test.result as? TvmMethodFailure)?.exitCode == 1000
            },
        )
    }
}
