package org.ton.blockchain.info

import org.ton.blockchain.JettonContractInfo
import org.ton.blockchain.JettonWalletInfo
import java.math.BigInteger

interface TonBlockchainExtendedInfoExtractor : TonBlockchainInfoExtractorWithHoldersInfo {
    fun getJettonContractInfo(address: String): JettonContractInfo
    fun getJettonAddresses(limit: Int, offset: Int): List<String>
    fun getJettonBalanceAndAddress(holderAddress: String, tokenAddress: String): Pair<String, BigInteger>?
    fun getLastTransactions(address: String, limit: Int): List<TransactionInfo>
    fun convertAddressToRawForm(address: String): String

    data class TransactionInfo(
        val isSuccess: Boolean,
        val opcode: UInt?,
        val sender: String?,  // null for external messages
    )
}
