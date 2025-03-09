package org.ton.blockchain.info

import org.ton.blockchain.ContractState

interface TonBlockchainInfoExtractor {
    fun getContractState(address: String): ContractState?
}
