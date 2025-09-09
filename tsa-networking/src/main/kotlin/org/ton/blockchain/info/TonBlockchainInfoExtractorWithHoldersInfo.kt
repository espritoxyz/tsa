package org.ton.blockchain.info

import org.ton.blockchain.JettonWalletInfo

interface TonBlockchainInfoExtractorWithHoldersInfo : TonBlockchainInfoExtractor {
    fun getJettonWallets(
        masterAddress: String,
        limit: Int = 100,
        offset: Int = 0,
    ): List<JettonWalletInfo>
}
