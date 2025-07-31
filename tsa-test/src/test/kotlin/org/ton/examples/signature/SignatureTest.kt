package org.ton.examples.signature

import org.ton.test.utils.TvmTestExecutor
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.gen.dsl.render.TsRenderer
import org.usvm.machine.TvmContext
import org.usvm.test.resolver.TvmMethodFailure
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignatureTest {
    private val signaturePath = "/signature/signature.fc"
    private val unreachableCodePath = "/signature/signature-unreachable.fc"

    private val passedSignatureCheckExitCode = 333

    @Test
    fun testSignature() {
        val path = extractResource(signaturePath)
        val result = funcCompileAndAnalyzeAllMethods(path)

        val signatureCheckPassed = result.testSuites
            .single { it.methodId == TvmContext.RECEIVE_INTERNAL_ID }
            .any { (it.result as? TvmMethodFailure)?.exitCode == passedSignatureCheckExitCode }

        assertTrue(signatureCheckPassed)

        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testDroppedState() {
        val path = extractResource(unreachableCodePath)
        val result = funcCompileAndAnalyzeAllMethods(path)

        val signatureCheckPassed = result.testSuites
            .single { it.methodId == TvmContext.RECEIVE_INTERNAL_ID }
            .any { (it.result as? TvmMethodFailure)?.exitCode == passedSignatureCheckExitCode }

        assertFalse(signatureCheckPassed)
    }
}
