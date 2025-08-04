package org.ton.blockchain.analyzer

import org.ton.blockchain.ContractState
import org.ton.blockchain.JettonContractInfo
import org.ton.blockchain.JettonWalletInfo
import org.ton.blockchain.info.TonBlockchainExtendedInfoExtractor

class TonBlockchainAnalyzerBasedOnExtendedInfoExtractor(
    val infoExtractor: TonBlockchainExtendedInfoExtractor
) : TvmBlockchainAnalyzer {
    override fun extractJettonContractInfo(address: String): JettonContractInfo {
        return infoExtractor.getJettonContractInfo(address)
    }

    override fun getContractState(address: String): ContractState? {
        return infoExtractor.getContractState(address)
    }

    override fun getJettonWalletInfo(jettonAddress: String, jettonState: ContractState, holderAddress: String): JettonWalletInfo? {
        val (walletAddress, balance) = infoExtractor.getJettonBalanceAndAddress(holderAddress, jettonAddress)
            ?: return null
        val state = getContractState(walletAddress)
            ?: return null
        return JettonWalletInfo(walletAddress, holderAddress, balance.toString(), state)
    }

    override fun getJettonWalletAddress(
        jettonAddress: String,
        jettonState: ContractState,
        holderAddress: String
    ): String {
        // [getJettonWalletInfo] cannot be used here, because we must calculate potential address even if it is not on blockchain
        return infoExtractor.runGetWalletAddressOnJettonMaster(jettonAddress, holderAddress)
    }
}
