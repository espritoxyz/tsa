package org.ton.blockchain.analyzer

import org.ton.blockchain.ContractState
import org.ton.blockchain.JettonContractInfo
import org.ton.blockchain.JettonWalletInfo
import org.ton.blockchain.emulator.TvmConcreteEmulator
import org.ton.blockchain.info.TonBlockchainInfoExtractor

interface TvmBlockchainAnalyzer {
    fun extractJettonContractInfo(address: String): JettonContractInfo
    fun getContractState(address: String): ContractState?
    fun getJettonWalletInfo(jettonAddress: String, jettonState: ContractState, holderAddress: String): JettonWalletInfo?
    fun getJettonWalletAddress(jettonAddress: String, jettonState: ContractState, holderAddress: String): String
}

abstract class TvmBlockchainAnalyzerBase(
    emulatorLibPath: String
) : TvmBlockchainAnalyzer {
    protected abstract val infoExtractor: TonBlockchainInfoExtractor
    private val emulator = TvmConcreteEmulator(emulatorLibPath)

    override fun extractJettonContractInfo(address: String): JettonContractInfo {
        val state = infoExtractor.getContractState(address)
            ?: error("No jetton at address $address")
        val jettonInfo = emulator.getJettonInfo(address, state)

        return processLibraryCells(jettonInfo)
    }

    override fun getContractState(address: String): ContractState? = infoExtractor.getContractState(address)

    override fun getJettonWalletInfo(
        jettonAddress: String,
        jettonState: ContractState,
        holderAddress: String
    ): JettonWalletInfo? {
        val walletAddress = getJettonWalletAddress(jettonAddress, jettonState, holderAddress)
        val walletState = getContractState(walletAddress)
            ?: return null
        val balance = emulator.getJettonWalletBalance(walletAddress, walletState)

        return JettonWalletInfo(walletAddress, holderAddress, balance.toString(), walletState)
    }

    override fun getJettonWalletAddress(
        jettonAddress: String,
        jettonState: ContractState,
        holderAddress: String
    ): String {
        return emulator.getWalletAddress(holderAddress, jettonAddress, jettonState)
    }

    protected abstract fun processLibraryCells(jettonContractInfo: JettonContractInfo): JettonContractInfo
}
