package org.ton.blockchain

import org.ton.block.AddrStd
import java.net.HttpURLConnection
import java.net.URI
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun makeRequest(query: String, failOnRequestError: Boolean = true): Pair<Int, String> {

    val connection = URI(query).toURL().openConnection() as? HttpURLConnection
        ?: error("Could not cast connection to HttpURLConnection")

    val responseCode = connection.responseCode
    if (!failOnRequestError && responseCode !in 200..<300) {
        return responseCode to ""
    }
    check(responseCode in 200..<300) {
        "Request $query returned response code $responseCode"
    }
    return responseCode to connection.inputStream.readBytes().decodeToString()
}

fun readableAddressToHex(address: String): String {
    return AddrStd.parse(address).address.toHex()
}

@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.toBase64() =
    Base64.Default.encode(this)

@OptIn(ExperimentalStdlibApi::class, ExperimentalEncodingApi::class)
fun String.base64ToHex() =
    Base64.Default.decode(this).toHexString()

@OptIn(ExperimentalStdlibApi::class)
fun String.hexToBase64() =
    hexToByteArray().toBase64()

fun String.toUrlAddress() = replace(":", "%3A")
