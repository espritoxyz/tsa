package org.ton.blockchain.analyzer

import org.ton.blockchain.ContractState
import org.ton.blockchain.JettonContractInfo
import org.ton.blockchain.base64ToHex
import org.ton.blockchain.fromBase64
import org.ton.blockchain.info.LiteServerBlockchainInfoExtractor

class LiteServerBlockchainAnalyzer(
    pathToTonLibJson: String,
    pathToConfig: String,
    emulatorLibPath: String,
) : TvmBlockchainAnalyzerBase(emulatorLibPath) {
    override val infoExtractor: LiteServerBlockchainInfoExtractor = LiteServerBlockchainInfoExtractor(
        pathToTonLibJson,
        pathToConfig
    )

    override fun processLibraryCells(jettonContractInfo: JettonContractInfo): JettonContractInfo {
        val processedWalletCode = infoExtractor.extractOrdinaryCellIfLibraryCellGiven(jettonContractInfo.walletContractBytes)
            ?.base64ToHex()
            ?: jettonContractInfo.walletContractBytesHex

        return jettonContractInfo.copy(walletContractBytesHex = processedWalletCode)
    }

    override fun extractLibraryCellByHashIfCan(libraryCellHash: ByteArray): ByteArray? {
        return infoExtractor.getLibraryCellValueByHash(libraryCellHash)?.fromBase64()
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun processStateWithPossibleLibraryCell(state: ContractState): ContractState {
        val processedWalletCode = infoExtractor.extractOrdinaryCellIfLibraryCellGiven(state.codeHex.hexToByteArray())
            ?.base64ToHex()
            ?: state.codeHex

        return ContractState(codeHex = processedWalletCode, dataHex = state.dataHex, balance = state.balance)
    }
}
