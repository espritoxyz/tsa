package org.ton.blockchain.info

import org.ton.blockchain.makeRequest
import java.util.Date
import kotlin.math.max

abstract class TonBlockchainInfoExtractorWithRequestPause(
    private val pauseBetweenRequestsMillies: Long,
) : TonBlockchainInfoExtractor {
    private var lastRequestTimestamp = 0L

    protected fun makeTonApiRequest(
        query: String,
        failOnRequestError: Boolean = true,
    ): Pair<Int, String> {
        val now = Date().time
        lastRequestTimestamp = Date().time
        Thread.sleep(max(0, pauseBetweenRequestsMillies - (now - lastRequestTimestamp)))
        return makeRequest(query, failOnRequestError)
    }
}

data class TonApiException(
    override val message: String,
    val isParsingError: Boolean,
) : RuntimeException()
