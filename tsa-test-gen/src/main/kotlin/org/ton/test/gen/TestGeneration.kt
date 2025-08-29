package org.ton.test.gen

import org.ton.test.gen.dsl.TsContext
import org.ton.test.gen.dsl.TsTestBlockBuilder
import org.ton.test.gen.dsl.TsTestFileBuilder
import org.ton.test.gen.dsl.models.TsBlockchain
import org.ton.test.gen.dsl.models.TsCell
import org.ton.test.gen.dsl.models.TsTestFile
import org.ton.test.gen.dsl.models.TsVariable
import org.ton.test.gen.dsl.models.blockchainCreate
import org.ton.test.gen.dsl.models.compileContract
import org.ton.test.gen.dsl.models.now
import org.ton.test.gen.dsl.models.openContract
import org.ton.test.gen.dsl.models.parseAddress
import org.ton.test.gen.dsl.models.toInt
import org.ton.test.gen.dsl.models.toTsValue
import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.gen.dsl.testFile
import org.ton.test.gen.dsl.wrapper.basic.TsBasicWrapperDescriptor
import org.ton.test.gen.dsl.wrapper.basic.constructor
import org.ton.test.gen.dsl.wrapper.basic.external
import org.ton.test.gen.dsl.wrapper.basic.initializeContract
import org.ton.test.gen.dsl.wrapper.basic.internal
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.ADDRESS_TAG_LENGTH
import org.usvm.machine.TvmContext.Companion.RECEIVE_EXTERNAL_ID
import org.usvm.machine.TvmContext.Companion.RECEIVE_INTERNAL_ID
import org.usvm.machine.TvmContext.Companion.STD_ADDRESS_TAG
import org.usvm.machine.state.TvmUserDefinedFailure
import org.usvm.test.resolver.truncateSliceCell
import org.usvm.test.resolver.TvmContractSymbolicTestResult
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmTerminalMethodSymbolicResult
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestInput
import org.usvm.test.resolver.TvmTestInput.RecvInternalInput
import org.usvm.test.resolver.TvmTestIntegerValue
import org.usvm.test.resolver.TvmTestSliceValue
import java.nio.file.Path
import java.util.Locale
import org.usvm.machine.TvmContext.Companion.stdMsgAddrSize
import org.usvm.test.minimization.minimizeTestCase
import kotlin.io.path.nameWithoutExtension

/**
 * @return the generated test file name, or `null` if test states are empty
 */
fun generateTests(
    analysisResult: TvmContractSymbolicTestResult,
    projectPath: Path,
    sourceRelativePath: Path,
    contractType: TsRenderer.ContractType,
    generateRecvExternalTests: Boolean = false,  // TODO: make `true` default (after fixes for recv_external)
    useMinimization: Boolean = false,
): String? {
    val entryTests = analysisResult.testSuites.flatten()

    return generateTests(
        entryTests,
        projectPath,
        sourceRelativePath,
        contractType,
        generateRecvExternalTests,
        useMinimization
    )
}

fun generateTests(
    tests: List<TvmSymbolicTest>,
    projectPath: Path,
    sourceRelativePath: Path,
    contractType: TsRenderer.ContractType,
    generateRecvExternalTests: Boolean = false,  // TODO: make `true` default (after fixes for recv_external)
    useMinimization: Boolean = false,
): String? {
    val name = extractContractName(sourceRelativePath)

    val ctx = TsContext()
    val test = ctx.constructTests(
        name,
        tests,
        sourceRelativePath.toString(),
        generateRecvExternalTests,
        useMinimization
    ) ?: return null

    val renderedTests = TsRenderer(ctx, contractType).renderTests(test)

    writeRenderedTest(projectPath, renderedTests)
    return renderedTests.fileName
}

private fun TsContext.constructTests(
    name: String,
    tests: List<TvmSymbolicTest>,
    sourcePath: String,
    generateRecvExternalTests: Boolean,
    useMinimization: Boolean,
): TsTestFile? {
    val recvInternalTests = tests
        .filter { it.methodId == RECEIVE_INTERNAL_ID && it.result is TvmMethodFailure }
        .let { if (useMinimization) minimizeTestCase(it) else it }

    val recvExternalTests = if (generateRecvExternalTests) {
        tests.filter {
            it.methodId == RECEIVE_EXTERNAL_ID && it.result is TvmMethodFailure &&
                (it.input as? TvmTestInput.RecvExternalInput)?.wasAccepted == true
        }.let { if (useMinimization) minimizeTestCase(it) else it }
    } else {
        emptyList()
    }

    if (recvInternalTests.isEmpty() && recvExternalTests.isEmpty()) {
        return null
    }

    return testFile(name) {
        val wrapperDescriptor = TsBasicWrapperDescriptor(this@constructTests, name)
        registerWrapper(wrapperDescriptor)

        val code = newVar("code", TsCell)
        val blockchain = newVar("blockchain", TsBlockchain)

        emptyLine()

        beforeAll {
            code assign compileContract(sourcePath)
        }

        emptyLine()

        registerRecvInternalTests(wrapperDescriptor, recvInternalTests, code, blockchain)

        registerRecvExternalTests(wrapperDescriptor, recvExternalTests, code, blockchain)
    }
}

private data class TestCaseContext(
    val testName: String,
    val test: TvmSymbolicTest,
    val code: TsVariable<TsCell>,
    val blockchain: TsVariable<TsBlockchain>,
)

private fun TsTestFileBuilder.registerTestsForMethod(
    testGroupName: String,
    tests: List<TvmSymbolicTest>,
    code: TsVariable<TsCell>,
    blockchain: TsVariable<TsBlockchain>,
    registerTestBlock: TsTestBlockBuilder.(TestCaseContext) -> Unit,
) {
    if (tests.isEmpty()) {
        return
    }

    val testsWithNames = generateTestNames(tests) zip tests

    describe(testGroupName) {
        testsWithNames.forEach { (name, test) ->
            val ctx = TestCaseContext(name, test, code, blockchain)
            registerTestBlock(ctx)
        }
    }
}

private fun TsTestFileBuilder.registerRecvInternalTests(
    wrapperDescriptor: TsBasicWrapperDescriptor,
    tests: List<TvmSymbolicTest>,
    code: TsVariable<TsCell>,
    blockchain: TsVariable<TsBlockchain>,
) {
    registerTestsForMethod(
        "tsa-tests-recv-internal",
        tests,
        code,
        blockchain,
    ) { ctx ->
        val input = resolveReceiveInternalInput(ctx.test)

        it(ctx.testName) {
            ctx.blockchain assign blockchainCreate(input.configHex)
            ctx.blockchain.now() assign ctx.test.time.toTsValue().toInt()

            emptyLine()

            val data = newVar("data", ctx.test.rootInitialData.toTsValue())
            val contractAddr = newVar("contractAddr", parseAddress(input.address))
            val contractBalance = newVar("contractBalance", input.initialBalance.toTsValue())

            emptyLine()

            val contract = newVar(
                "contract",
                ctx.blockchain.openContract(wrapperDescriptor.constructor(contractAddr, ctx.code, data))
            )
            +contract.initializeContract(ctx.blockchain, contractBalance)

            emptyLine()

            val srcAddr = newVar("srcAddr", parseAddress(input.srcAddress))
            val msgBody = newVar("msgBody", input.msgBodyCell.toTsValue())
            val msgCurrency = newVar("msgCurrency", input.input.msgValue.toTsValue())
            val bounce = newVar("bounce", input.input.bounce.toTsValue())
            val bounced = newVar("bounced", input.input.bounced.toTsValue())
            val ihrDisabled = newVar("ihrDisabled", input.input.ihrDisabled.toTsValue())
            val ihrFee = newVar("ihrFee", input.input.ihrFee.toTsValue())
            val forwardFee = newVar("forwardFee", input.input.fwdFee.toTsValue())
            val createdLt = newVar("createdLt", input.input.createdLt.toTsValue())
            val createdAt = newVar("createdAt", input.input.createdAt.value.toLong().toTsValue()) // createdAt is a number, not a bigint

            emptyLine()

            val sendMessageResult = newVar(
                "sendMessageResult",
                contract.internal(
                    ctx.blockchain,
                    srcAddr,
                    msgBody,
                    msgCurrency,
                    bounce,
                    bounced,
                    ihrDisabled,
                    ihrFee,
                    forwardFee,
                    createdLt,
                    createdAt,
                )
            )
            sendMessageResult.expectToHaveTransaction {
                from = srcAddr
                to = contractAddr
                exitCode = input.exitCode.toTsValue()
            }
        }
    }
}

private fun TsTestFileBuilder.registerRecvExternalTests(
    wrapperDescriptor: TsBasicWrapperDescriptor,
    tests: List<TvmSymbolicTest>,
    code: TsVariable<TsCell>,
    blockchain: TsVariable<TsBlockchain>,
) {
    registerTestsForMethod(
        "tsa-tests-recv-external",
        tests,
        code,
        blockchain,
    ) { ctx ->
        val input = resolveReceiveExternalInput(ctx.test)

        it(ctx.testName) {
            ctx.blockchain assign blockchainCreate(input.configHex)
            ctx.blockchain.now() assign ctx.test.time.toTsValue().toInt()

            emptyLine()

            val data = newVar("data", ctx.test.rootInitialData.toTsValue())
            val contractAddr = newVar("contractAddr", parseAddress(input.address))
            val contractBalance = newVar("contractBalance", input.initialBalance.toTsValue())

            emptyLine()

            val contract = newVar(
                "contract",
                ctx.blockchain.openContract(wrapperDescriptor.constructor(contractAddr, ctx.code, data))
            )
            +contract.initializeContract(ctx.blockchain, contractBalance)

            emptyLine()

            val msgBody = newVar("msgBody", input.msgBody.toTsValue())

            emptyLine()

            val sendMessageResult = newVar(
                "sendMessageResult",
                contract.external(ctx.blockchain, msgBody)
            )
            sendMessageResult.expectToHaveTransaction {
                to = contractAddr
                exitCode = input.exitCode.toTsValue()
            }
        }
    }
}

private fun resolveReceiveInternalInput(test: TvmSymbolicTest): TvmReceiveInternalInput {
    val input = test.input as? RecvInternalInput
        ?: error("Unexpected general input ${test.input} for recv_internal")

    val configHex = transformTestConfigIntoHex(test.config)

    val contractAddress = extractAddress(test.contractAddress.data)
        ?: error("Unexpected incorrect contract address ${test.contractAddress.data}")
    val srcAddress = extractAddress(input.srcAddress.cell.data)
        ?: error("Incorrect srcAddress ${input.srcAddress.cell.data}")

    val result = test.result
    require(result is TvmTerminalMethodSymbolicResult) {
        "Unexpected test result: $result"
    }

    return TvmReceiveInternalInput(
        configHex = configHex,
        msgBodyCell = truncateSliceCell(input.msgBody),
        initialBalance = test.initialRootContractBalance,
        time = test.time,
        address = contractAddress,
        srcAddress = srcAddress,
        input = input,
        exitCode = result.exitCode,
    )
}

private data class TvmReceiveInternalInput(
    val configHex: String,
    val msgBodyCell: TvmTestDataCellValue,
    val initialBalance: TvmTestIntegerValue,
    val time: TvmTestIntegerValue,
    val address: String,
    val srcAddress: String,
    val input: RecvInternalInput,
    val exitCode: Int,
)

private fun resolveReceiveExternalInput(test: TvmSymbolicTest): TvmReceiveExternalInput {
    // TODO: for now, take into account only in_message
    // in theory, up to 4 arguments can be used (same as for receive_internal)
    val usedParameters = (test.input as? TvmTestInput.StackInput)?.usedParameters
        // TODO support recv_external input
        ?: error("Unexpected input ${test.input} for recv_external")

    val args = usedParameters.reversed()
    val msgBody = args.getOrNull(0)
        ?: TvmTestSliceValue()

    require(msgBody is TvmTestSliceValue) {
        "Unexpected recv_external arg at index 0: $msgBody"
    }

    val configHex = transformTestConfigIntoHex(test.config)

    val balance = test.initialRootContractBalance

    val contractAddress = extractAddress(test.contractAddress.data)
        ?: error("Unexpected incorrect contract address")

    val result = test.result
    require(result is TvmTerminalMethodSymbolicResult) {
        "Unexpected test result: $result"
    }

    return TvmReceiveExternalInput(
        configHex,
        truncateSliceCell(msgBody),
        contractAddress,
        balance,
        test.time,
        result.exitCode,
    )
}

private data class TvmReceiveExternalInput(
    val configHex: String,
    val msgBody: TvmTestDataCellValue,
    val address: String,
    val initialBalance: TvmTestIntegerValue,
    val time: TvmTestIntegerValue,
    val exitCode: Int,
)

private fun generateTestNames(tests: List<TvmSymbolicTest>): List<String> {
    val exitsCounter = mutableMapOf<String, Int>()

    return tests.map { test ->
        val exit = (test.result as? TvmMethodFailure)?.failure?.exit
        val exitName = if (exit is TvmUserDefinedFailure) {
            "${exit.ruleName}-${exit.exitCode}"
        } else {
            exit?.ruleName ?: "successful"
        }
        val testIdx = exitsCounter.getOrDefault(exitName, 0)

        exitsCounter[exitName] = testIdx + 1

        "$exitName-$testIdx"
    }
}

private fun extractContractName(sourceRelativePath: Path): String {
    val fileName = sourceRelativePath.fileName.nameWithoutExtension
    val words = fileName.split('_', '-', '.')
    val capitalizedWords = words.map { word ->
        word.replaceFirstChar { firstChar ->
            if (firstChar.isLowerCase()) {
                firstChar.titlecase(Locale.getDefault())
            } else {
                firstChar.toString()
            }
        }
    }

    return capitalizedWords.joinToString(separator = "").let {
        // Sometimes, the file with sources can start with digits (for example, if this is a temporary file with BoC from Fift)
        // In this case, we have to add something before contract name to avoid TS syntax errors
        if (it.first().isDigit()) {
            "Contract$it"
        } else {
            it
        }
    }
}

private fun loadGrams(bits: String): String? {
    val len = bits.take(4).binaryToUnsignedBigInteger().toInt()

    if (bits.length < 4 + len * 8) {
        return null
    }

    return bits.drop(4).take(len * 8)
}

private fun skipGrams(bits: String): String? {
    val gramsStr = loadGrams(bits)
        ?: return null

    return bits.drop(4 + gramsStr.length)
}

private fun skipStdAddress(bits: String): String? {
    val tag = bits.take(ADDRESS_TAG_LENGTH)

    if (bits.length < stdMsgAddrSize || tag != STD_ADDRESS_TAG) {
        return null
    }

    return bits.drop(stdMsgAddrSize)
}

private fun extractAddress(bits: String): String? {
    // addr_std$10 anycast:(Maybe Anycast) workchain_id:int8 address:bits256
    val stdAddrLength = ADDRESS_TAG_LENGTH + 1 + TvmContext.STD_WORKCHAIN_BITS + TvmContext.ADDRESS_BITS

    // TODO for now assume that the address is addr_std$10
    if (bits.length < stdAddrLength || bits.take(2) != STD_ADDRESS_TAG) {
        return null
    }

    val workchainBin = bits.drop(3).take(TvmContext.STD_WORKCHAIN_BITS)
    val addrBin = bits.drop(11).take(TvmContext.ADDRESS_BITS)

    val workchain = workchainBin.binaryToSignedDecimal()
    val addr = addrBin.binaryToHex()
    val paddedAddr = addr.padStart(TvmContext.ADDRESS_BITS / 4, '0')

    return "$workchain:$paddedAddr"
}
