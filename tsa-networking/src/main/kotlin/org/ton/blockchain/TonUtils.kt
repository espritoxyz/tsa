package org.ton.blockchain

import org.ton.bitstring.BitString
import org.ton.bitstring.toBitString
import org.ton.block.AddrStd
import org.ton.cell.Cell
import org.ton.cell.CellType
import java.net.HttpURLConnection
import java.net.URI
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun makeRequest(
    query: String,
    failOnRequestError: Boolean = true,
): Pair<Int, String> {
    val connection =
        URI(query).toURL().openConnection() as? HttpURLConnection
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

fun readableAddressToHex(address: String): String = AddrStd.parse(address).address.toHex()

@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.toBase64() = Base64.Default.encode(this)

@OptIn(ExperimentalStdlibApi::class, ExperimentalEncodingApi::class)
fun String.base64ToHex() = Base64.Default.decode(this).toHexString()

@OptIn(ExperimentalEncodingApi::class)
fun String.fromBase64() = Base64.Default.decode(this)

@OptIn(ExperimentalStdlibApi::class)
fun String.hexToBase64() = hexToByteArray().toBase64()

fun String.toUrlAddress() = replace(":", "%3A")

fun extractHashFromLibraryCell(cell: Cell): BitString? {
    if (cell.type != CellType.LIBRARY_REFERENCE) {
        return null
    }
    return cell.bits.drop(cell.bits.size - 256).toBitString()
}
