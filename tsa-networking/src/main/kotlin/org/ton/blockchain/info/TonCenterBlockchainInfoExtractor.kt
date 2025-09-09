package org.ton.blockchain.info

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ton.blockchain.ContractState
import org.ton.blockchain.JettonWalletInfo
import org.ton.blockchain.base64ToHex
import org.ton.blockchain.toUrlAddress

class TonCenterBlockchainInfoExtractor(
    pauseBetweenRequestsMillies: Long,
    private val apiKey: String? = null,
) : TonBlockchainInfoExtractorWithRequestPause(pauseBetweenRequestsMillies),
    TonBlockchainInfoExtractorWithHoldersInfo {
    override fun getContractState(address: String): ContractState? {
        val response =
            runCatching {
                var request = "$API_URL/v3/addressInformation?address=${address.toUrlAddress()}&use_v2=true"
                if (apiKey != null) {
                    request += "&api_key=$apiKey"
                }
                val (code, res) = makeTonApiRequest(request, failOnRequestError = false)

                if (code == 404) {
                    return null
                }

                check(code in 200..<300) {
                    "Request $request returned response code $code"
                }
                res
            }.getOrElse { error ->
                throw TonApiException("TonCenter request failed: $error", isParsingError = false)
            }

        return runCatching {
            val body = Json.parseToJsonElement(response)
            val data = body.jsonObject["data"]!!.jsonPrimitive.content
            val code = body.jsonObject["code"]!!.jsonPrimitive.content
            val balance =
                body.jsonObject["balance"]!!
                    .jsonPrimitive.content
                    .toLong()

            // non-existent address
            if (code.isEmpty() || code == "null") {
                return null
            }

            ContractState(data.base64ToHex(), code.base64ToHex(), balance)
        }.getOrElse {
            val msg = "Could not parse output of contractInfo: $response"
            throw TonApiException(msg, isParsingError = true)
        }
    }

    override fun getJettonWallets(
        masterAddress: String,
        limit: Int,
        offset: Int,
    ): List<JettonWalletInfo> {
        val response =
            runCatching {
                var request =
                    "$API_URL/v3/jetton/wallets?jetton_address=${masterAddress.toUrlAddress()}&limit=$limit&offset=$offset&exclude_zero_balance=true"
                if (apiKey != null) {
                    request += "&api_key=$apiKey"
                }
                val (code, res) = makeTonApiRequest(request, failOnRequestError = true)

                check(code in 200..<300) {
                    "Request $request returned response code $code"
                }
                res
            }.getOrElse { error ->
                throw TonApiException("TonCenter request failed: $error", isParsingError = false)
            }

        return runCatching {
            val body = Json.parseToJsonElement(response)
            body.jsonObject["jetton_wallets"]!!.jsonArray.map {
                val address = it.jsonObject["address"]!!.jsonPrimitive.content
                val owner = it.jsonObject["owner"]!!.jsonPrimitive.content
                val balance = it.jsonObject["balance"]!!.jsonPrimitive.content
                val state =
                    getContractState(address)
                        ?: error("Contract at $address must be present")
                JettonWalletInfo(address, owner, balance, state)
            }
        }.getOrElse { error ->
            val msg = "Could not parse output of getJettonWallets (error $error): $response"
            throw TonApiException(msg, isParsingError = true)
        }
    }

    companion object {
        private const val API_URL = "https://toncenter.com/api"
    }
}
