package org.ton.blockchain.analyzer

import org.ton.blockchain.ContractState
import org.ton.blockchain.JettonContractInfo
import org.ton.blockchain.JettonWalletInfo
import org.ton.blockchain.emulator.MissingLibraryException
import org.ton.blockchain.emulator.TvmConcreteEmulator
import org.ton.blockchain.info.TonBlockchainInfoExtractor
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import java.math.BigInteger

interface TvmBlockchainAnalyzer {
    fun extractJettonContractInfo(address: String): JettonContractInfo

    fun getContractState(address: String): ContractState?

    fun getJettonWalletInfo(
        jettonAddress: String,
        jettonState: ContractState,
        holderAddress: String
    ): JettonWalletInfo?

    fun getJettonWalletAddress(
        jettonAddress: String,
        jettonState: ContractState,
        holderAddress: String
    ): String
}

abstract class TvmBlockchainAnalyzerBase(
    emulatorLibPath: String
) : TvmBlockchainAnalyzer {
    protected abstract val infoExtractor: TonBlockchainInfoExtractor
    private val emulator = TvmConcreteEmulator(emulatorLibPath)

    @OptIn(ExperimentalStdlibApi::class)
    private fun <T> runWithCatchingMissingLibrary(emulatorRun: (Map<BigInteger, Cell>) -> T): T {
        val libs = mutableMapOf<BigInteger, Cell>()

        while (true) {
            try {
                return emulatorRun(libs)
            } catch (e: MissingLibraryException) {
                val keyAsByteArray = e.library.hexToByteArray()

                val libCellValue =
                    extractLibraryCellByHashIfCan(keyAsByteArray)
                        ?: throw e

                val keyAsBigInteger = BigInteger(ByteArray(1) + keyAsByteArray)
                val prevValue = libs.put(keyAsBigInteger, BagOfCells(libCellValue).roots.first())

                // to catch infinite loops
                check(prevValue == null) {
                    "Cell for hash ${e.library} was already put in library map"
                }
            }
        }
    }

    override fun extractJettonContractInfo(address: String): JettonContractInfo {
        val state =
            infoExtractor.getContractState(address)
                ?: error("No jetton at address $address")

        return runWithCatchingMissingLibrary {
            val jettonInfo = emulator.getJettonInfo(address, state, it)
            processLibraryCells(jettonInfo)
        }
    }

    override fun getContractState(address: String): ContractState? = infoExtractor.getContractState(address)

    override fun getJettonWalletInfo(
        jettonAddress: String,
        jettonState: ContractState,
        holderAddress: String
    ): JettonWalletInfo? {
        val walletAddress = getJettonWalletAddress(jettonAddress, jettonState, holderAddress)
        val walletStateRaw =
            getContractState(walletAddress)
                ?: return null

        val walletState = processStateWithPossibleLibraryCell(walletStateRaw)

        val balance =
            runWithCatchingMissingLibrary {
                emulator.getJettonWalletBalance(walletAddress, walletState, it)
            }

        return JettonWalletInfo(walletAddress, holderAddress, balance.toString(), walletState)
    }

    override fun getJettonWalletAddress(
        jettonAddress: String,
        jettonState: ContractState,
        holderAddress: String
    ): String =
        runWithCatchingMissingLibrary {
            emulator.getWalletAddress(holderAddress, jettonAddress, jettonState, it)
        }

    protected open fun extractLibraryCellByHashIfCan(libraryCellHash: ByteArray): ByteArray? = null

    protected open fun processLibraryCells(jettonContractInfo: JettonContractInfo): JettonContractInfo =
        jettonContractInfo

    protected open fun processStateWithPossibleLibraryCell(state: ContractState): ContractState = state
}
