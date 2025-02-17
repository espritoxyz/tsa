package org.ton

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ton.boc.BagOfCells
import java.net.HttpURLConnection
import java.net.URI
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val TON_API_V2 = "https://tonapi.io/v2"

@OptIn(ExperimentalStdlibApi::class, ExperimentalEncodingApi::class)
fun extractJettonContractInfo(rawAddress: String): JettonContractInfo {
    val addressForUrl = rawAddress.replace(":", "%3A")
    val response = makeRequest("$TON_API_V2/blockchain/accounts/$addressForUrl/methods/get_jetton_data")
    return runCatching {
        val jsonArray = Json.parseToJsonElement(response).jsonObject["stack"]!!.jsonArray
        val code = jsonArray[4].jsonObject["cell"]!!.jsonPrimitive.content
        val codeHash = Base64.Default.encode(BagOfCells(code.hexToByteArray()).roots.first().hash().toByteArray())
        JettonContractInfo(
            contractBytesHex = code,
            jettonWalletCodeHashBase64 = codeHash,
        )
    }.getOrElse {
        error("Could not extract jetton-wallet code from query response (exception $it): $response")
    }
}

fun makeRequest(query: String): String {
    val connection = URI(query).toURL().openConnection() as? HttpURLConnection
        ?: error("Could not cast connection to HttpURLConnection")
    val responseCode = connection.responseCode
    check(responseCode in 200..<300) {
        "Request $query returned response code $responseCode"
    }
    return connection.inputStream.readBytes().decodeToString()
}

data class JettonContractInfo(
    val contractBytesHex: String,
    val jettonWalletCodeHashBase64: String,
) {
    @OptIn(ExperimentalStdlibApi::class)
    val contractBytes: ByteArray
        get() = contractBytesHex.hexToByteArray()
}