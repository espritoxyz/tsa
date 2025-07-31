package org.ton.examples.bounce

import org.ton.test.utils.checkInvariants
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.usvm.machine.TvmOptions
import org.usvm.machine.getResourcePath
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmTestInput
import kotlin.test.Test

class BouncedMessageTest {
    private val bounceAssertsPath = "/bounce/bounce_asserts.fc"

    @Test
    fun testBounceInput() {
        val resourcePath = getResourcePath<BouncedMessageTest>(bounceAssertsPath)
        val options = TvmOptions(analyzeBouncedMessaged = true)
        val results = funcCompileAndAnalyzeAllMethods(resourcePath, tvmOptions = options)
        val tests = results.testSuites.single()

        checkInvariants(
            tests,
            listOf {
                test -> test.result is TvmSuccessfulExecution
            }
        )

        propertiesFound(
            tests,
            listOf {
                test -> (test.input as? TvmTestInput.RecvInternalInput)?.bounced == true
            }
        )
    }
}
