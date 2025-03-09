package org.ton.examples.args

import org.ton.examples.TvmTestExecutor
import org.ton.examples.funcCompileAndAnalyzeAllMethods
import org.ton.test.gen.dsl.render.TsRenderer
import org.usvm.machine.getResourcePath
import kotlin.test.Test

class ArgsConstraintsTest {
    private val consistentMsgValuePath = "/args/consistent_msg_value.fc"
    private val consistentFlagsPath = "/args/consistent_flags.fc"
    private val ihrFeePath = "/args/ihr_fee.fc"
    private val fwdFeePath = "/args/fwd_fee.fc"
    private val createdLtPath = "/args/created_lt.fc"
    private val createdAtPath = "/args/created_at.fc"

    @Test
    fun testConsistentMessageValue() {
        val path = getResourcePath<ArgsConstraintsTest>(consistentMsgValuePath)
        val result = funcCompileAndAnalyzeAllMethods(path)
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testConsistentFlags() {
        val path = getResourcePath<ArgsConstraintsTest>(consistentFlagsPath)
        val result = funcCompileAndAnalyzeAllMethods(path)
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testIhrFee() {
        val path = getResourcePath<ArgsConstraintsTest>(ihrFeePath)
        val result = funcCompileAndAnalyzeAllMethods(path)
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
}
