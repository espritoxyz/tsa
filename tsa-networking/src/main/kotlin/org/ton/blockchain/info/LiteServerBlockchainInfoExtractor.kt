package org.ton.blockchain.info

import org.ton.bitstring.toBitString
import org.ton.blockchain.ContractState
import org.ton.blockchain.base64ToHex
import org.ton.blockchain.toBase64
import org.ton.boc.BagOfCells
import org.ton.cell.CellType
import org.ton.java.tonlib.Tonlib
import org.ton.java.tonlib.types.AccountAddressOnly
import org.ton.java.tonlib.types.VerbosityLevel


class LiteServerBlockchainInfoExtractor(
    pathToTonLibJson: String,
    pathToConfig: String,
) : TonBlockchainInfoExtractor {

    private val tonlib: Tonlib = Tonlib.builder()
        .pathToTonlibSharedLib(pathToTonLibJson)
        .pathToGlobalConfig(pathToConfig)
        .verbosityLevel(VerbosityLevel.FATAL)
        .receiveRetryTimes(2)
        .receiveTimeout(2.0)
        .build()

    override fun getContractState(address: String): ContractState? {
        val accountAddressOnly = AccountAddressOnly.builder()
            .account_address(address)
            .build()
        val result = tonlib.getAccountState(accountAddressOnly)
        val balance = result.balance.toLong()
        if (
            balance == -1L ||
            result.account_state == null ||
            result.account_state.code == null ||
            result.account_state.data == null
        ) {
            return null  // no active contract at this address
        }
        return ContractState(
            dataHex = result.account_state.data.base64ToHex(),
            codeHex = result.account_state.code.base64ToHex(),
            balance = balance,
        )
    }

    fun extractOrdinaryCellIfLibraryCellGiven(cellBytes: ByteArray): String? {
        val cell = BagOfCells(cellBytes).roots.first()
        if (cell.type != CellType.LIBRARY_REFERENCE) {
            return null
        }
        val cellHashBase64 = cell.bits.drop(cell.bits.size - 256).toBitString().toByteArray().toBase64()

        val serverResult = tonlib.getLibraries(listOf(cellHashBase64))
        val result = serverResult.result
        check(result.size == 1) {
            "Unexpected server result: $result"
        }

        val entry = result.single()
        return entry.data
    }
}
