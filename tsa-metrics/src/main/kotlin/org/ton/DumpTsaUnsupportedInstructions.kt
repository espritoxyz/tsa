package org.ton

import mu.KLogging
import org.ton.bytecode.MethodId
import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.TvmMainMethod
import org.ton.bytecode.TvmMethod
import org.ton.bytecode.tvmDefaultInstructions
import org.ton.cell.Cell
import org.usvm.machine.DEFAULT_CONTRACT_DATA_HEX
import org.usvm.machine.TvmComponents
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmMachine
import org.usvm.machine.TvmOptions
import org.usvm.machine.interpreter.TvmInterpreter
import org.usvm.machine.toTvmCell
import java.io.File
import java.math.BigInteger

val logger = object : KLogging() {}.logger

private const val DEFAULT_REPORT_PATH: String = "unsupported-instructions.csv"

// TODO use kmetr with ClickHouse?
fun main() {
    val reportPath = System.getenv("TSA_UNSUPPORTED_INSTRUCTIONS_REPORT_PATH")
        ?: DEFAULT_REPORT_PATH

    TvmComponents(TvmMachine.defaultOptions).use { dummyComponents ->
        TvmContext(TvmOptions(), dummyComponents).use { ctx ->
            val dummyContractData = Cell.Companion.of(DEFAULT_CONTRACT_DATA_HEX)
            val dummyCodeCell = dummyContractData.toTvmCell()

            // Group instructions by category in alphabetical order
            val result = sortedMapOf<String, MutableList<String>>()

            tvmDefaultInstructions.entries.forEach { (group, insts) ->
                insts.forEach {
                    logger.debug { "Checking ${it.mnemonic}..." }

                    val code = TsaContractCode(
                        mainMethod = TvmMainMethod(mutableListOf(it)),
                        methods = mapOf(MethodId.ZERO to TvmMethod(MethodId.ZERO, mutableListOf(it))),
                        codeCell = dummyCodeCell
                    )

                    val dummyInterpreter = TvmInterpreter(
                        ctx,
                        listOf(code),
                        dummyComponents.typeSystem,
                        TvmInputInfo(),
                    )
                    val dummyState =
                        dummyInterpreter.getInitialState(startContractId = 0, dummyContractData, BigInteger.ZERO)

                    runCatching {
                        try {
                            dummyInterpreter.step(dummyState)
                        } catch (e: NotImplementedError) {
                            logger.debug { "${it.mnemonic} is not implemented!" }
                            result.getOrPut(group) { mutableListOf() }.add(it.mnemonic)
                        }
                    }
                }
            }

            val reportFile = File(reportPath)
            reportFile.parentFile.mkdirs()
            reportFile.createNewFile() // Ensure that for each run we have a fresh report file even if all instructions are implemented

            if (result.isEmpty()) {
                logger.info { "All instructions are implemented!" }
                return
            }

            // Save to CSV
            reportFile.bufferedWriter().use {
                it.write("Category, instructions\n")
                result.forEach { (category, insts) ->
                    if (insts.isEmpty()) {
                        return@forEach
                    }

                    // Sort instructions in alphabetical order
                    insts.sort()

                    val lineElements = listOf(category) + insts
                    it.write(lineElements.joinToString(", ") + "\n")
                }
            }
        }
    }
}
