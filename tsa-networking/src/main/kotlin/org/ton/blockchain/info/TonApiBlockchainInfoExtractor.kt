package org.ton.blockchain.info

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.ton.block.AddrStd
import org.ton.block.MsgAddress
import org.ton.blockchain.ContractState
import org.ton.blockchain.JettonContractInfo
import org.ton.blockchain.JettonWalletInfo
import org.ton.blockchain.toUrlAddress
import org.ton.boc.BagOfCells
import java.math.BigInteger

class TonApiBlockchainInfoExtractor(
    private val tonApiProvider: String,
    pauseBetweenRequestsMillies: Long,
) : TonBlockchainInfoExtractorWithRequestPause(pauseBetweenRequestsMillies),
    TonBlockchainExtendedInfoExtractor {
    override fun getContractState(address: String): ContractState? {
        val response =
            runCatching {
                val request = "$tonApiProvider/v2/blockchain/accounts/${address.toUrlAddress()}"
                val (code, res) = makeTonApiRequest(request, failOnRequestError = false)

                if (code == 404) {
                    return null
                }

                check(code in 200..<300) {
                    "Request $request returned response code $code"
                }
                res
            }.getOrElse { error ->
                throw TonApiException("TonAPI request failed: $error", isParsingError = false)
            }

        return runCatching {
            val body = Json.parseToJsonElement(response)
            // data and code are null for uninit addresses
            val data =
                body.jsonObject["data"]?.jsonPrimitive?.content
                    ?: return null
            val code =
                body.jsonObject["code"]?.jsonPrimitive?.content
                    ?: return null
            val balance = body.jsonObject["balance"]!!.jsonPrimitive.long

            ContractState(data, code, balance)
        }.getOrElse {
            val msg = "Could not parse output of contractInfo: $response"
            throw TonApiException(msg, isParsingError = true)
        }
    }

    override fun getJettonContractInfo(address: String): JettonContractInfo {
        val addressForUrl = address.toUrlAddress()

        val state =
            getContractState(address = addressForUrl)
                ?: error("No jetton at address $address")

        val (_, responseFromGetMethod) =
            runCatching {
                makeTonApiRequest("$tonApiProvider/v2/blockchain/accounts/$addressForUrl/methods/get_jetton_data")
            }.getOrElse { error ->
                throw TonApiException("TonAPI request failed: $error", isParsingError = false)
            }
        return runCatching {
            val jsonArray = Json.parseToJsonElement(responseFromGetMethod).jsonObject["stack"]!!.jsonArray
            val totalSupply =
                BigInteger(
                    jsonArray[0]
                        .jsonObject["num"]!!
                        .jsonPrimitive.content
                        .removePrefix("0x"),
                    16,
                )
            val isMintable = jsonArray[1].jsonObject["num"]!!.jsonPrimitive.content != "0x0"
            val owner = parseOwner(jsonArray[2].jsonObject["cell"]!!.jsonPrimitive.content)
            val code = jsonArray[4].jsonObject["cell"]!!.jsonPrimitive.content
            JettonContractInfo(
                masterState = state,
                walletContractBytesHex = code,
                declaredOwner = owner,
                declaredMintable = isMintable,
                declaredTotalSupply = totalSupply.toString(),
            )
        }.getOrElse {
            val msg = "Could not extract jetton-wallet code from query response (exception $it): $responseFromGetMethod"
            throw TonApiException(msg, isParsingError = true)
        }
    }

    override fun getJettonAddresses(
        limit: Int,
        offset: Int,
    ): List<String> {
        val (_, resp) =
            runCatching {
                makeTonApiRequest("$tonApiProvider/v2/jettons?limit=$limit&offset=$offset")
            }.getOrElse { error ->
                throw TonApiException("TonAPI request failed: $error", isParsingError = false)
            }
        return runCatching {
            Json.parseToJsonElement(resp).jsonObject["jettons"]!!.jsonArray.mapNotNull {
                val name =
                    it.jsonObject["metadata"]!!
                        .jsonObject["name"]!!
                        .jsonPrimitive.content
                if (name.startsWith("DeDust")) {
                    null
                } else {
                    it.jsonObject["metadata"]!!
                        .jsonObject["address"]!!
                        .jsonPrimitive.content
                }
            }
        }.getOrElse {
            val msg = "Could not parse output of getJettons: $resp"
            throw TonApiException(msg, isParsingError = true)
        }
    }

    override fun getJettonWallets(
        masterAddress: String,
        limit: Int,
        offset: Int,
    ): List<JettonWalletInfo> {
        val (_, response) =
            runCatching {
                makeTonApiRequest(
                    "$tonApiProvider/v2/jettons/${masterAddress.toUrlAddress()}/holders?limit=$limit&offset=$offset",
                )
            }.getOrElse { error ->
                throw TonApiException("TonAPI request failed: $error", isParsingError = false)
            }

        return runCatching {
            Json.parseToJsonElement(response).jsonObject["addresses"]!!.jsonArray.map { holder ->
                val address = holder.jsonObject["address"]!!.jsonPrimitive.content
                val owner =
                    holder.jsonObject["owner"]!!
                        .jsonObject["address"]!!
                        .jsonPrimitive.content
                val balance = holder.jsonObject["balance"]!!.jsonPrimitive.content
                val init =
                    getContractState(address)
                        ?: error("Contract at $address must be present")

                JettonWalletInfo(address, owner, balance, init)
            }
        }.getOrElse {
            val msg = "Could not parse output of jettonHolders: $response"
            throw TonApiException(msg, isParsingError = true)
        }
    }

    override fun getJettonBalanceAndAddress(
        holderAddress: String,
        tokenAddress: String,
    ): Pair<String, BigInteger>? {
        val query = "$tonApiProvider/v2/accounts/${holderAddress.toUrlAddress()}/jettons/${tokenAddress.toUrlAddress()}"
        val (code, resp) =
            makeTonApiRequest(query, failOnRequestError = false)
        if (code == 404) {
            return null
        }
        if (code !in 200..<300) {
            throw TonApiException("Request $query returned response code $code", isParsingError = false)
        }
        return runCatching {
            val value =
                Json
                    .parseToJsonElement(resp)
                    .jsonObject["balance"]!!
                    .jsonPrimitive.content
            val balance = value.toBigInteger()
            val address =
                Json
                    .parseToJsonElement(resp)
                    .jsonObject["wallet_address"]!!
                    .jsonObject["address"]!!
                    .jsonPrimitive
                    .content
            address to balance
        }.getOrElse {
            val msg = "Could not parse output of getJettonBalance: $resp"
            throw TonApiException(msg, isParsingError = true)
        }
    }

    override fun getLastTransactions(
        address: String,
        limit: Int,
    ): List<TonBlockchainExtendedInfoExtractor.TransactionInfo> {
        val query =
            "$tonApiProvider/v2/blockchain/accounts/${address.toUrlAddress()}/transactions?limit=$limit&sort_order=desc"
        val (_, resp) = makeTonApiRequest(query)
        return runCatching {
            val parsed = Json.parseToJsonElement(resp).jsonObject["transactions"]!!
            parsed.jsonArray.mapNotNull { transactionInfo ->
                val isSuccess = transactionInfo.jsonObject["success"]!!.jsonPrimitive.boolean
                val inMsgInfo =
                    transactionInfo.jsonObject["in_msg"]
                        ?: return@mapNotNull null
                val opcode =
                    inMsgInfo.jsonObject["op_code"]
                        ?.jsonPrimitive
                        ?.content
                        ?.removePrefix("0x")
                        ?.toUInt(radix = 16)
                val sender =
                    inMsgInfo.jsonObject["source"]
                        ?.jsonObject
                        ?.get("address")
                        ?.jsonPrimitive
                        ?.content
                TonBlockchainExtendedInfoExtractor.TransactionInfo(isSuccess, opcode, sender)
            }
        }.getOrElse {
            val msg = "Could not parse output of getLastTransactions: $resp. Exception: $it"
            throw TonApiException(msg, isParsingError = true)
        }
    }

    override fun convertAddressToRawForm(address: String): String {
        val query = "$tonApiProvider/v2/address/${address.toUrlAddress()}/parse"
        val (_, resp) = makeTonApiRequest(query)
        return runCatching {
            val parsed = Json.parseToJsonElement(resp)
            parsed.jsonObject["raw_form"]!!
                .jsonPrimitive.content
                .lowercase()
        }.getOrElse {
            val msg = "Could not parse output of convertAddressToRawForm: $resp"
            throw TonApiException(msg, isParsingError = true)
        }
    }

    override fun runGetWalletAddressOnJettonMaster(
        masterAddress: String,
        holderAddress: String,
    ): String {
        val query =
            "$tonApiProvider/v2/blockchain/accounts/${masterAddress.toUrlAddress()}/" +
                "methods/get_wallet_address?args=${holderAddress.toUrlAddress()}"
        val (_, resp) = makeTonApiRequest(query)
        return runCatching {
            val parsed = Json.parseToJsonElement(resp)
            parsed.jsonObject["decoded"]!!
                .jsonObject["jetton_wallet_address"]!!
                .jsonPrimitive.content
                .lowercase()
        }.getOrElse {
            val msg = "Could not parse output of convertAddressToRawForm: $resp"
            throw TonApiException(msg, isParsingError = true)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun parseOwner(ownerBocHex: String): String {
        val boc = BagOfCells(ownerBocHex.hexToByteArray())
        val address = boc.roots.first()
        val stdAddress =
            runCatching {
                MsgAddress.loadTlb(address) as? AddrStd
            }.getOrNull()
                ?: return address.bits.toHex()
        val workchain = stdAddress.workchainId
        val parsedAddress = stdAddress.address.toHex()
        return "$workchain:$parsedAddress"
    }
}
