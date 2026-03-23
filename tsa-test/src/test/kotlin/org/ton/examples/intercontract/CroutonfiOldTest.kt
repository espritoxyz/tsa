package org.ton.examples.intercontract

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.ton.RUN_HARD_TESTS_REGEX
import org.ton.RUN_HARD_TESTS_VAR
import org.ton.bytecode.TvmRealInst
import org.ton.test.utils.exitCode
import org.ton.test.utils.extractBocContractFromResource
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractCommunicationSchemeFromResource
import org.ton.test.utils.extractConcreteDataFromResource
import org.ton.test.utils.getAddressBits
import org.ton.test.utils.propertiesFound
import org.usvm.machine.ExploreExitCodesStopStrategy
import org.usvm.machine.FollowTrace
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.test.resolver.TvmTestFailure
import java.io.File
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CroutonfiOldTest {
    private val checkerPath = "/intercontract/croutonfi-old/checker.fc"
    private val schemePath = "/intercontract/croutonfi-old/scheme.json"
    private val vaultCodePath = "/intercontract/croutonfi-old/vault_code.boc"
    private val vaultDataPath = "/intercontract/croutonfi-old/vault_data.boc"
    private val poolCodePath = "/intercontract/croutonfi-old/pool_code.boc"
    private val poolDataPath = "/intercontract/croutonfi-old/pool_data.boc"

//    @EnabledIfEnvironmentVariable(named = RUN_HARD_TESTS_VAR, matches = RUN_HARD_TESTS_REGEX)
    @Test
    fun findTonDrain() {
        val checkerContract = extractCheckerContractFromResource(checkerPath)
        val vaultContract = extractBocContractFromResource(vaultCodePath)
        val poolContract = extractBocContractFromResource(poolCodePath)
        val communicationScheme = extractCommunicationSchemeFromResource(schemePath)

        val trace = File("path.txt").readLines().map { line ->
            val parts = line.split(" ")
            if (parts.size == 2) {
                val hex = parts[0]
                val offset = parts[1].toIntOrNull()
                if (offset != null && hex.all { it.isDigit() || it in 'A'..'F' }) {
                    hex to offset
                } else {
                    null
                }
            } else {
                null
            }
        }

        val options =
            TvmOptions(
                useReceiverInputs = false,
                performAdditionalChecksWhileResolving = true,
                intercontractOptions = IntercontractOptions(communicationScheme = communicationScheme),
                turnOnTLBParsingChecks = false,
                enableOutMessageAnalysis = true,
                stopOnFirstError = false,
//                timeout = 40.minutes,
                solverTimeout = 5.seconds,
                loopIterationLimit = 3,
                useIntBlasting = true,
                trace = FollowTrace(trace)
            )

        val concreteVaultData =
            TvmConcreteContractData(
//                addressBits = getAddressBits("0:1d5fdacd17489f917240a3b097839bfbf3205b3fd3b52f850beccf442345cc92"),
//                initialBalance = 2148977707770L.toBigInteger(),
//                contractC4 = extractConcreteDataFromResource(vaultDataPath),
            )

        val concretePoolData =
            TvmConcreteContractData(
//                addressBits = getAddressBits("0:7b3abba2d73fdd28e3681ee825be2d9b314a660f87f0d19e02da07b00f614fd0"),
//                initialBalance = 32106961941L.toBigInteger(),
//                contractC4 = extractConcreteDataFromResource(poolDataPath),
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

        propertiesFound(
            tests,
            listOf { test ->
                (test.result as? TvmTestFailure)?.exitCode == 1000
            },
        )

//        val test = tests.first { it.exitCode() == 1000 }
//        val loc = test.coveredInstructions.map { (it as? TvmRealInst)?.physicalLocation }
//        val serialized = loc.joinToString(separator = "\n") { "${it?.cellHashHex} ${it?.offset}" }
//        val f = File("path.txt")
//        f.writeText(serialized)
//        f.createNewFile()
    }
}
