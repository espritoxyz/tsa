package org.ton.blockchain.info

import org.ton.blockchain.JettonContractInfo
import org.ton.blockchain.JettonWalletInfo
import java.math.BigInteger

interface TonBlockchainExtendedInfoExtractor : TonBlockchainInfoExtractor {
    fun getJettonContractInfo(address: String): JettonContractInfo
    fun getJettonAddresses(limit: Int, offset: Int): List<String>
    fun getJettonBalanceAndAddress(holderAddress: String, tokenAddress: String): Pair<String, BigInteger>?
    fun getJettonWallets(masterAddress: String, limit: Int = 100, offset: Int = 0): List<JettonWalletInfo>
}
