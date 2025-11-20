package org.ton.examples.args

import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.utils.TvmTestExecutor
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmConcreteGeneralData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.getResourcePath
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmTestFailure
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertTrue

class ArgsConstraintsTest {
    private val consistentMsgValuePath = "/args/consistent_msg_value.fc"
    private val consistentFlagsPath = "/args/consistent_flags.fc"
    private val fwdFeePath = "/args/fwd_fee.fc"
    private val createdLtPath = "/args/created_lt.fc"
    private val createdAtPath = "/args/created_at.fc"
    private val myAddressPath = "/args/my_address.fc"
    private val balancePath = "/args/balance.fc"
    private val senderAddressPath = "/args/sender_address.fc"
    private val opcodePath = "/args/opcode.fc"
    private val recvExternalPath = "/args/recv_external.fc"

    @Test
    fun testConsistentMessageValue() {
        val path = getResourcePath<ArgsConstraintsTest>(consistentMsgValuePath)
        val result = funcCompileAndAnalyzeAllMethods(path)
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testConsistentFlags() {
        val path = getResourcePath<ArgsConstraintsTest>(consistentFlagsPath)
        val result =
            funcCompileAndAnalyzeAllMethods(
                path,
                tvmOptions = TvmOptions(analyzeBouncedMessaged = true),
            )
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testFwdFee() {
        val path = getResourcePath<ArgsConstraintsTest>(fwdFeePath)
        val result = funcCompileAndAnalyzeAllMethods(path)
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testCreatedLt() {
        val path = getResourcePath<ArgsConstraintsTest>(createdLtPath)
        val result = funcCompileAndAnalyzeAllMethods(path)
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testCreatedAt() {
        val path = getResourcePath<ArgsConstraintsTest>(createdAtPath)
        val result = funcCompileAndAnalyzeAllMethods(path)
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testMyAddress() {
        val path = getResourcePath<ArgsConstraintsTest>(myAddressPath)
        val result = funcCompileAndAnalyzeAllMethods(path)

        propertiesFound(
            result.testSuites.single(),
            listOf(
                { test -> test.result is TvmSuccessfulExecution },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 1000 },
            ),
        )

        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    private val stonfiAddressBits =
        "10000000000" +
            BigInteger("779dcc815138d9500e449c5291e7f12738c23d575b5310000f6a253bd607384e", 16)
                .toString(2)
                .padStart(256, '0')

    @Test
    fun testConcreteMyAddress() {
        val path = getResourcePath<ArgsConstraintsTest>(myAddressPath)
        val result =
            funcCompileAndAnalyzeAllMethods(
                path,
                concreteContractData =
                    TvmConcreteContractData(
                        addressBits = stonfiAddressBits,
                    ),
            )

        val tests = result.testSuites.single()

        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode == 1000 },
        )

        checkInvariants(
            tests,
            listOf { test -> test.result !is TvmSuccessfulExecution },
        )
    }

    @Test
    fun testBalance() {
        val path = getResourcePath<ArgsConstraintsTest>(balancePath)
        val result = funcCompileAndAnalyzeAllMethods(path)

        val tests = result.testSuites.single()

        propertiesFound(
            tests,
            listOf(
                { test -> test.result is TvmSuccessfulExecution },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 1001 },
            ),
        )

        checkInvariants(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode != 1000 },
        )

        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testConcreteBalance() {
        val path = getResourcePath<ArgsConstraintsTest>(balancePath)
        val result =
            funcCompileAndAnalyzeAllMethods(
                path,
                concreteContractData = TvmConcreteContractData(initialBalance = 12345.toBigInteger()),
            )

        val tests = result.testSuites.single()
        assertTrue { tests.isNotEmpty() }

        checkInvariants(
            tests,
            listOf { test -> test.result !is TvmSuccessfulExecution },
        )
    }

    @Test
    fun testSenderAddress() {
        val path = getResourcePath<ArgsConstraintsTest>(senderAddressPath)
        val result = funcCompileAndAnalyzeAllMethods(path)

        propertiesFound(
            result.testSuites.single(),
            listOf(
                { test -> test.result is TvmSuccessfulExecution },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 1000 },
            ),
        )

        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testConcreteSenderAddress() {
        val path = getResourcePath<ArgsConstraintsTest>(senderAddressPath)
        val result =
            funcCompileAndAnalyzeAllMethods(
                path,
                concreteGeneralData = TvmConcreteGeneralData(initialSenderBits = stonfiAddressBits),
            )

        propertiesFound(
            result.testSuites.single(),
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode == 1000 },
        )

        checkInvariants(
            result.testSuites.single(),
            listOf { test -> test.result !is TvmSuccessfulExecution },
        )
    }

    @Test
    fun testOpcode() {
        val path = getResourcePath<ArgsConstraintsTest>(opcodePath)
        val result = funcCompileAndAnalyzeAllMethods(path)

        propertiesFound(
            result.testSuites.single(),
            listOf(
                { test -> test.result is TvmSuccessfulExecution },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 1000 },
            ),
        )

        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testConcreteOpcode() {
        val path = getResourcePath<ArgsConstraintsTest>(opcodePath)
        val result =
            funcCompileAndAnalyzeAllMethods(
                path,
                concreteGeneralData = TvmConcreteGeneralData(initialOpcode = 0x12345678U),
            )

        propertiesFound(
            result.testSuites.single(),
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode == 1000 },
        )

        checkInvariants(
            result.testSuites.single(),
            listOf { test -> test.result !is TvmSuccessfulExecution },
        )
    }

    @Test
    fun testRecvExternal() {
        val path = extractResource(recvExternalPath)
        val result = funcCompileAndAnalyzeAllMethods(path)

        val tests = result.first { it.methodId == TvmContext.RECEIVE_EXTERNAL_ID }

        checkInvariants(
            tests,
            listOf(
                { test -> (test.result as? TvmTestFailure)?.exitCode != 1000 },
                { test -> (test.result as? TvmTestFailure)?.exitCode != 1001 },
                { test -> (test.result as? TvmTestFailure)?.exitCode != 1002 },
            ),
        )

        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode == 1003 },
        )
    }
}
