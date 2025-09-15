package org.ton

import mu.KLogging
import org.ton.bytecode.MethodId
import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.TvmCell
import org.ton.bytecode.TvmCellData
import org.ton.bytecode.TvmMainMethod
import org.ton.bytecode.TvmMethod
import org.ton.bytecode.tvmDefaultInstructions
import org.usvm.machine.TvmComponents
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmConcreteGeneralData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.interpreter.TvmInterpreter
import java.io.File
import java.math.BigInteger

val logger = object : KLogging() {}.logger

private const val DEFAULT_REPORT_PATH: String = "unsupported-instructions.csv"

// TODO use kmetr with ClickHouse?
fun main() {
    val reportPath =
        System.getenv("TSA_UNSUPPORTED_INSTRUCTIONS_REPORT_PATH")
            ?: DEFAULT_REPORT_PATH

    TvmComponents(TvmOptions()).use { dummyComponents ->
        TvmContext(TvmOptions(), dummyComponents).use { ctx ->
            val dummyCodeCell = TvmCell(TvmCellData(""), emptyList())

            // Group instructions by category in alphabetical order
            val result = sortedMapOf<String, MutableList<String>>()

            tvmDefaultInstructions.entries.forEach { (group, insts) ->
                insts.forEach {
                    logger.debug { "Checking ${it.mnemonic}..." }

                    val code =
                        TsaContractCode(
                            mainMethod = TvmMainMethod(mutableListOf(it)),
                            methods = mapOf(MethodId.ZERO to TvmMethod(MethodId.ZERO, mutableListOf(it))),
                            codeCell = dummyCodeCell,
                        )

                    val dummyInterpreter =
                        TvmInterpreter(
                            ctx,
                            listOf(code),
                            dummyComponents.typeSystem,
                            TvmInputInfo(),
                        )
                    val dummyState =
                        dummyInterpreter.getInitialState(
                            startContractId = 0,
                            TvmConcreteGeneralData(),
                            listOf(TvmConcreteContractData()),
                            BigInteger.ZERO,
                        )

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
            reportFile.parentFile?.mkdirs()
            // Ensure that for each run we have a fresh report file even if all instructions are implemented
            reportFile.createNewFile()

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
