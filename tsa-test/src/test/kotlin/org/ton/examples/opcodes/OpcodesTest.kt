package org.ton.examples.opcodes

import org.ton.test.utils.FIFT_STDLIB_RESOURCE
import org.ton.test.utils.exitCode
import org.ton.test.utils.extractResource
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TimeDivisionBetweenOpcodes
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOpcodeExtractor
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import kotlin.test.Test
import kotlin.test.assertEquals

class OpcodesTest {
    private val oneOpcodePath = "/opcodes/one_opcode.fc"
    private val checkerPath = "/checkers/send_internal.fc"

    @Test
    fun testOpcodeExtraction() {
        val path = extractResource(oneOpcodePath)
        val contract = getFuncContract(path, FIFT_STDLIB_RESOURCE)

        val extractor = TvmOpcodeExtractor()
        val opcodes = extractor.extractOpcodes(contract)

        assertEquals(setOf(0x12345678L.toBigInteger()), opcodes)
    }

    @Test
    fun testOpcodePathSelector() {
        val path = extractResource(oneOpcodePath)
        val contract = getFuncContract(path, FIFT_STDLIB_RESOURCE)

        val checker =
            getFuncContract(
                extractResource(checkerPath),
                FIFT_STDLIB_RESOURCE,
                isTSAChecker = true,
            )

        val options =
            TvmOptions(
                stopOnFirstError = true,
                divideTimeBetweenOpcodes =
                    TimeDivisionBetweenOpcodes(
                        inputId = 0,
                        opcodes = setOf(0x12345678L.toBigInteger()),
                    ),
            )

        val tests =
            analyzeInterContract(
                listOf(checker, contract),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        propertiesFound(
            tests,
            listOf(
                { it.exitCode() == 1000 },
                { it.exitCode() == 1001 },
                { it.exitCode() == 1002 },
            ),
        )
    }
}
