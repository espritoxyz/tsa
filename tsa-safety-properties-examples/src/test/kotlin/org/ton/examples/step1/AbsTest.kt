package org.ton.examples.step1

import FIFT_STDLIB_RESOURCE
import FUNC_STDLIB_RESOURCE
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.machine.getResourcePath
import org.usvm.machine.state.TvmIntegerOverflowError
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmTestIntegerValue
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class AbsTest {
    private val absContractResourcePath = "/examples/step1/abs.fc"
    private val checkerResourcePath = "/examples/step1/abs_checker.fc"

    private val tvmIntMinValue = BigInteger.TWO.pow(256).negate()

    @Test
    fun testAbsChecker() {
        val absContractPath = getResourcePath<AbsTest>(absContractResourcePath)
        val absContractCode = getFuncContract(absContractPath, FUNC_STDLIB_RESOURCE, FIFT_STDLIB_RESOURCE)

        val checkerPath = getResourcePath<AbsTest>(checkerResourcePath)
        val checkerCode = getFuncContract(checkerPath, FUNC_STDLIB_RESOURCE, FIFT_STDLIB_RESOURCE, isTSAChecker = true)

        val options = TvmOptions(
            intercontractOptions = IntercontractOptions(communicationScheme = null),
            turnOnTLBParsingChecks = false,
        )

        val contracts = listOf(checkerCode, absContractCode)
        val result = analyzeInterContract(
            contracts,
            startContractId = 0, // Checker contract is the first to analyze
            methodId = TvmContext.RECEIVE_INTERNAL_ID,
            options = options,
        )

        val integerOverflowFailure = result.tests.single {
            (it.result as? TvmMethodFailure)?.failure?.exit == TvmIntegerOverflowError
        }
        val valueForAbs = integerOverflowFailure.fetchedValues[0]!!

        assertEquals(tvmIntMinValue, (valueForAbs as TvmTestIntegerValue).value)
    }
}
