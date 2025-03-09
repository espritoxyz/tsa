package org.ton.blockchain.analyzer

import org.ton.blockchain.JettonContractInfo
import org.ton.blockchain.base64ToHex
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
}
