package org.ton.blockchain.analyzer

import org.ton.blockchain.JettonContractInfo
import org.ton.blockchain.info.TonBlockchainInfoExtractor

class TonApiAndEmulationBlockchainAnalyzer(
    emulatorLibPath: String,
    override val infoExtractor: TonBlockchainInfoExtractor,
) : TvmBlockchainAnalyzerBase(
    emulatorLibPath = emulatorLibPath
) {
    // library cells are not supported for TON API yet
    override fun processLibraryCells(jettonContractInfo: JettonContractInfo): JettonContractInfo = jettonContractInfo
}
