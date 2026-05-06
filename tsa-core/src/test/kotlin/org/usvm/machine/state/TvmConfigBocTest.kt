package org.usvm.machine.state

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class TvmConfigBocTest {
    @Test
    fun `config entries contain key 18`() {
        val entries = TvmConfigBoc.entries
        println("Config entries keys: ${entries.keys.sorted()}")
        assertTrue(18 in entries, "Config must contain key 18 (storage prices)")
        assertTrue(20 in entries, "Config must contain key 20 (MC gas prices)")
        assertTrue(21 in entries, "Config must contain key 21 (gas prices)")
        assertTrue(24 in entries, "Config must contain key 24 (MC msg prices)")
        assertTrue(25 in entries, "Config must contain key 25 (msg prices)")
    }

    @Test
    fun `storage prices are parseable`() {
        val prices = TvmConfigBoc.storagePrices
        println("Storage prices: $prices")
        assertTrue(prices.bitPricePs > 0)
        assertTrue(prices.cellPricePs > 0)
    }

    @Test
    fun `gas prices are parseable`() {
        val mcGas = TvmConfigBoc.masterchainGasPrices
        val bcGas = TvmConfigBoc.basechainGasPrices
        println("MC gas prices: $mcGas")
        println("BC gas prices: $bcGas")
        assertTrue(mcGas.gasPrice > 0)
        assertTrue(bcGas.gasPrice > 0)
    }

    @Test
    fun `msg prices are parseable`() {
        val mcMsg = TvmConfigBoc.masterchainMsgPrices
        val bcMsg = TvmConfigBoc.basechainMsgPrices
        println("MC msg prices: $mcMsg")
        println("BC msg prices: $bcMsg")
        assertTrue(mcMsg.lumpPrice > 0)
        assertTrue(bcMsg.lumpPrice > 0)
    }
}
