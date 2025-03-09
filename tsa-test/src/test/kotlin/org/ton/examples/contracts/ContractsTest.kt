package org.ton.examples.contracts

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.ton.bytecode.MethodId
import org.ton.examples.FIFT_STDLIB_RESOURCE
import org.ton.examples.TvmTestExecutor
import org.ton.examples.checkAtLeastOneStateForAllMethods
import org.ton.examples.compileAndAnalyzeFift
import org.ton.examples.extractResource
import org.ton.examples.funcCompileAndAnalyzeAllMethods
import org.ton.runHardTestsRegex
import org.ton.runHardTestsVar
import org.ton.test.gen.dsl.render.TsRenderer
import org.usvm.machine.BocAnalyzer
import org.usvm.machine.FiftAnalyzer
import org.usvm.machine.TvmOptions
import org.usvm.machine.getResourcePath
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue


class ContractsTest {
    private val nftItemPath: String = "/contracts/nft-item/nft-item.fc"
    private val walletV4Path: String = "/contracts/wallet-v4/wallet-v4-code.fc"
    private val walletV5Path: String = "/contracts/wallet-v5/wallet_v5.fc"
    private val subscriptionPluginPath: String = "/contracts/wallet-v4/simple-subscription-plugin.fc"
    private val jettonMinterPath: String = "/contracts/modern-jetton/jetton-minter.func"
    private val jettonWalletPath: String = "/contracts/modern-jetton/jetton-wallet.func"
    private val universalLockupWalletPath: String = "/contracts/universal-lockup-wallet/uni-lockup-wallet.fc"
    private val vestingLockupWalletPath: String = "/contracts/vesting-lockup-wallet/vesting-lockup-wallet.fc"
    private val bridgePath: String = "/contracts/bridge/bridge_code.fc"
    private val bridgeMultisigPath: String = "/contracts/bridge/multisig-code.fc"
    private val bridgeVotesCollectorPath: String = "/contracts/bridge/votes-collector.fc"
    private val multisigPath: String = "/contracts/multisig/multisig-code.fc"
    private val storagePath: String = "/contracts/storage/storage-contract.fc"
    private val storageProviderPath: String = "/contracts/storage/storage-provider.fc"
    private val vestingPath: String = "/contracts/vesting/vesting_wallet.fc"
    private val singleNominatorPath: String = "/contracts/single-nominator/single-nominator.fc"
    private val nominatorPoolPath: String = "/contracts/nominator-pool/pool.fc"
    private val stocksPath: String = "/contracts/stocks/stock_options.fc"
    private val configPath: String = "/contracts/config/config-code.fc"
    private val dnsAutoPath: String = "/contracts/dns/dns-auto-code.fc"
    private val dnsManualPath: String = "/contracts/dns/dns-manual-code.fc"
    private val electorPath: String = "/contracts/elector/elector-code.fc"
    private val highloadWalletPath: String = "/contracts/highload-wallet/highload-wallet-code.fc"
    private val highloadWalletV2Path: String = "/contracts/highload-wallet/highload-wallet-v2-code.fc"
    private val paymentChannelPath: String = "/contracts/payment-channel/payment-channel-code.fc"
    private val powTestgiverPath: String = "/contracts/pow-testgiver/pow-testgiver-code.fc"
    private val restrictedWalletPath: String = "/contracts/restricted-wallet/restricted-wallet-code.fc"
    private val restrictedWalletV2Path: String = "/contracts/restricted-wallet/restricted-wallet2-code.fc"
    private val restrictedWalletV3Path: String = "/contracts/restricted-wallet/restricted-wallet3-code.fc"
    private val walletV3Path: String = "/contracts/wallet-v3/wallet-v3-code.fc"

    private val walletV3FiftPath: String = "/contracts/wallet-v3/wallet-v3-code.fif"

    private val pumpersPath: String = "/contracts/EQCV_FsDSymN83YeKZKj_7sgwQHV0jJhCTvX5SkPHHxVOi0D.boc"
    private val jettonWalletWithConfigInstsPath: String = "/contracts/EQCnKexvdRR56OpxG2jpSUk0Dn6XpcgwHqE5A5plDz3TOQOt.boc"

    @EnabledIfEnvironmentVariable(named = runHardTestsVar, matches = runHardTestsRegex)
    @Test
    fun testPumpersMaster() {
        // TODO: one test fails concrete execution + sometimes test generation fails itself
        analyzeSpecificMethodBoc(pumpersPath, MethodId.ZERO, enableTestGeneration = false)
    }

    @Test
    fun testJettonWalletWithConfigInsts() {
        analyzeSpecificMethodBoc(jettonWalletWithConfigInstsPath, MethodId.ZERO, enableTestGeneration = true)
    }

    @Test
    fun testStocks() {
        analyzeFuncContract(stocksPath, methodsNumber = 6, enableTestGeneration = true)
    }

    @Test
    fun testWalletV4() {
        analyzeFuncContract(walletV4Path, methodsNumber = 7, enableTestGeneration = true)
    }

    @Ignore("slow hash validation https://github.com/explyt/tsa/issues/112")
    @Test
    fun testWalletV5() {
        analyzeFuncContract(walletV5Path, methodsNumber = 7, enableTestGeneration = true)
    }

    @EnabledIfEnvironmentVariable(named = runHardTestsVar, matches = runHardTestsRegex)
    @Test
    fun nftItem() {
        analyzeFuncContract(nftItemPath, methodsNumber = 15, enableTestGeneration = true)
    }

    @Test
    fun jettonMinter() {
        analyzeFuncContract(jettonMinterPath, methodsNumber = 4, enableTestGeneration = true)
    }

    @Test
    fun jettonWallet() {
        analyzeFuncContract(jettonWalletPath, methodsNumber = 3, enableTestGeneration = true)
    }

    @Test
    fun singleNominator() {
        analyzeFuncContract(singleNominatorPath, methodsNumber = 3, enableTestGeneration = true)
    }

    @Test
    fun storage() {
        analyzeFuncContract(storagePath, methodsNumber = 7, enableTestGeneration = true)
    }

    @Test
    fun vestingLockupWallet() {
        analyzeFuncContract(vestingLockupWalletPath, methodsNumber = 6, enableTestGeneration = true)
    }

    @Test
    fun testSubscriptionPlugin() {
        analyzeFuncContract(subscriptionPluginPath, methodsNumber = 4, enableTestGeneration = true)
    }

    @Test
    fun bridge() {
        analyzeFuncContract(bridgePath, methodsNumber = 8, enableTestGeneration = true)
    }

    @Test
    fun bridgeVotesCollector() {
        // TODO unexpected overflow errors during DICTUDELGET:
        //  "cannot change label of an old dictionary cell while merging edges"
        analyzeFuncContract(bridgeVotesCollectorPath, methodsNumber = 5, enableTestGeneration = false)
    }

    @EnabledIfEnvironmentVariable(named = runHardTestsVar, matches = runHardTestsRegex)
    @Test
    fun nominatorPool() {
        // long test execution (4 min)
        analyzeFuncContract(nominatorPoolPath, methodsNumber = 10, enableTestGeneration = false)
    }

    @Ignore("slow hash validation https://github.com/explyt/tsa/issues/112")
    @Test
    fun multisig() {
        analyzeFuncContract(multisigPath, methodsNumber = 16, enableTestGeneration = true)
    }

    @Ignore("ksmt bug https://github.com/UnitTestBot/ksmt/issues/160")
    @Test
    fun bridgeMultisig() {
        analyzeFuncContract(bridgeMultisigPath, methodsNumber = 18, enableTestGeneration = true)
    }

    @Test
    fun storageProvider() {
        analyzeFuncContract(storageProviderPath, methodsNumber = 10, enableTestGeneration = true)
    }

    @Test
    fun vesting() {
        analyzeFuncContract(vestingPath, methodsNumber = 9, enableTestGeneration = true)
    }

    @Ignore("PFXDICTGETQ is not supported")
    @Test
    fun universalLockupWallet() {
        analyzeFuncContract(universalLockupWalletPath, methodsNumber = 13, enableTestGeneration = true)
    }

    @Test
    fun testWalletV3Fift() {
        // TODO: make tests for recvExternal pass
        analyzeSpecificMethodFift(walletV3FiftPath, methodId = MethodId.valueOf(-1), enableTestGeneration = false)

        // no tests will be generated for these two for now
        analyzeSpecificMethodFift(walletV3FiftPath, methodId = MethodId.valueOf(85143), enableTestGeneration = false)
        analyzeSpecificMethodFift(walletV3FiftPath, methodId = MethodId.ZERO, enableTestGeneration = false)
    }

    @Test
    fun walletV3() {
        analyzeFuncContract(walletV3Path, methodsNumber = 4, enableTestGeneration = true)
    }

    @Ignore
    @Test
    fun config() {
        // TODO support config params 9, 10, 11
        // TODO support BLESS, RANDU256
        analyzeFuncContract(configPath, methodsNumber = 3, enableTestGeneration = true)
    }

    @Ignore
    @Test
    fun dnsAuto() {
        // TODO support CDATASIZE, SHA256U
        analyzeFuncContract(dnsAutoPath, methodsNumber = 3, enableTestGeneration = true)
    }

    @Ignore
    @Test
    fun dnsManual() {
        // TODO support PFXDICTSET, PFXDICTDEL, PFXDICTGETQ
        analyzeFuncContract(dnsManualPath, methodsNumber = 3, enableTestGeneration = true)
    }

    @Ignore
    @Test
    fun elector() {
        // TODO support config params 36
        // TODO fix resolver
        analyzeFuncContract(electorPath, methodsNumber = 3, enableTestGeneration = true)
    }

    @Test
    fun highloadWallet() {
        analyzeFuncContract(highloadWalletPath, methodsNumber = 4, enableTestGeneration = true)
    }

    @Test
    fun highloadWalletV2() {
        analyzeFuncContract(highloadWalletV2Path, methodsNumber = 4, enableTestGeneration = true)
    }

    @Ignore
    @Test
    fun paymentChannel() {
        // TODO long with_close analysis
        //  large cells of max depth
        analyzeFuncContract(paymentChannelPath, methodsNumber = 4, enableTestGeneration = true)
    }

    @Ignore
    @Test
    fun powTestgiver() {
        // TODO support ADDRAND
        analyzeFuncContract(powTestgiverPath, methodsNumber = 4, enableTestGeneration = true)
    }

    @Test
    fun restrictedWallet() {
        analyzeFuncContract(restrictedWalletPath, methodsNumber = 5, enableTestGeneration = true)
    }

    @Test
    fun restrictedWallet2() {
        analyzeFuncContract(restrictedWalletV2Path, methodsNumber = 6, enableTestGeneration = true)
    }

    @Test
    fun restrictedWallet3() {
        analyzeFuncContract(restrictedWalletV3Path, methodsNumber = 7, enableTestGeneration = true)
    }


    private fun analyzeFuncContract(
        contractPath: String,
        methodsNumber: Int,
        methodsBlackList: Set<MethodId> = hashSetOf(),
        enableTestGeneration: Boolean
    ) {
        val funcResourcePath = extractResource(contractPath)

        val methodStates = funcCompileAndAnalyzeAllMethods(funcResourcePath, methodsBlackList = methodsBlackList)
        checkAtLeastOneStateForAllMethods(methodsNumber = methodsNumber, methodStates)

        if (enableTestGeneration) {
            TvmTestExecutor.executeGeneratedTests(methodStates, funcResourcePath, TsRenderer.ContractType.Func)
        }
    }

    private fun analyzeSpecificMethodFift(
        contractPath: String,
        methodId: MethodId,
        enableTestGeneration: Boolean,
    ) {
        val fiftPath = getResourcePath<ContractsTest>(contractPath)
        val tests = compileAndAnalyzeFift(fiftPath, methodId)
        assertTrue { tests.isNotEmpty() }
        if (enableTestGeneration) {
            val bocFile = createTempFile()
            try {
                FiftAnalyzer(fiftStdlibPath = FIFT_STDLIB_RESOURCE).compileFiftToBoc(fiftPath, bocFile)
                TvmTestExecutor.executeGeneratedTests(tests, bocFile, TsRenderer.ContractType.Boc)
            } finally {
                bocFile.deleteIfExists()
            }
        }
    }

    private fun analyzeSpecificMethodBoc(
        contractPath: String,
        methodId: MethodId,
        enableTestGeneration: Boolean,
    ) {
        val bocPath = getResourcePath<ContractsTest>(contractPath)
        val tests = BocAnalyzer.analyzeSpecificMethod(
            bocPath,
            methodId
        )
        assertTrue { tests.isNotEmpty() }

        if (enableTestGeneration) {
            TvmTestExecutor.executeGeneratedTests(tests, bocPath, TsRenderer.ContractType.Boc)
        }
    }
}
