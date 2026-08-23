package org.ton.sarif

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ton.boc.BagOfCells
import org.usvm.machine.toBase64
import org.usvm.test.resolver.TvmTestAuthValue
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.transformTestCellIntoCell
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals

class SarifReportTest {
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `authorized code includes canonical code hash in base64`() {
        val code =
            TvmTestDataCellValue(
                data = "10101100",
                refs = listOf(TvmTestDataCellValue(data = "101")),
            )
        val json = convertAuthorizedCodeToJson(TvmTestAuthValue.AuthorizedCode(code)).jsonObject
        val expectedHashBase64 =
            Base64.Default.encode(
                transformTestCellIntoCell(code).hash().toByteArray(),
            )
        val bocHashBase64 =
            Base64.Default.encode(
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(BagOfCells(transformTestCellIntoCell(code)).toByteArray()),
            )

        assertEquals("code", json.getValue("type").jsonPrimitive.content)
        assertEquals(code.toBase64(), json.getValue("code").jsonPrimitive.content)
        assertEquals(expectedHashBase64, json.getValue("codeHashBase64").jsonPrimitive.content)
        assert(json.getValue("codeHashBase64").jsonPrimitive.content != bocHashBase64)
    }
}
