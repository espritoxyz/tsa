package org.ton.examples.constraints

import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.usvm.machine.TvmOptions
import org.usvm.test.resolver.TvmSuccessfulExecution
import kotlin.io.path.Path
import kotlin.test.Test

class RecvInternalConstraintsTest {
    private val recvInternalConstraintsPath: String = "/constraints/recv-internal-constraints.fc"

    @Test
    fun testRecvInternalConstraints() {
        val codeResourcePath = this::class.java.getResource(recvInternalConstraintsPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource bytecode $recvInternalConstraintsPath")

        val options = TvmOptions(useRecvInternalInput = true, turnOnTLBParsingChecks = false)
        val methodStates = funcCompileAndAnalyzeAllMethods(codeResourcePath, tvmOptions = options)
        val results = methodStates.testSuites.flatMap { it.tests }

        assert(results.single().result is TvmSuccessfulExecution)
    }
}