package org.ton.examples.checkers

import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.exitCode
import org.ton.test.utils.extractTolkCheckerContractFromResource
import org.ton.test.utils.extractTolkContractFromResource
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import kotlin.test.Test

class TolkSourcesTest {
    private val checkerPath = "/checkers/tolk-example/checker.tolk"
    private val checkeePath = "/checkers/tolk-example/send-single-message.tolk"

    @Test
    fun `tolk testdrive`() {
        val checker = extractTolkCheckerContractFromResource(checkerPath)
        val checkee = extractTolkContractFromResource(checkeePath)

        val options =
            TvmOptions(
                stopOnFirstError = true,
            )
        val tests =
            analyzeInterContract(
                listOf(checker, checkee),
                startContractId = 0,
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )
        tests.assertPropertiesFound({ test -> test.exitCode() == 401 })
    }
}
