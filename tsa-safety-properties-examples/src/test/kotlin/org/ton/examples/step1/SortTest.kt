package org.ton.examples.step1

import FIFT_STDLIB_RESOURCE
import org.usvm.machine.IntercontractOptions
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.getFuncContract
import org.usvm.machine.getResourcePath
import org.usvm.test.resolver.TvmTestFailure
import org.usvm.test.resolver.TvmTestIntegerValue
import kotlin.test.Test
import kotlin.test.assertTrue

class SortTest {
    private val sortContractResourcePath = "/examples/step1/sort.fc"
    private val checkerResourcePath = "/examples/step1/sort_checker.fc"

    @Test
    fun testSortChecker() {
        val sortContractPath = getResourcePath<SortTest>(sortContractResourcePath)
        val sortContractCode = getFuncContract(sortContractPath, FIFT_STDLIB_RESOURCE)

        val checkerPath = getResourcePath<SortTest>(checkerResourcePath)
        val checkerCode = getFuncContract(checkerPath, FIFT_STDLIB_RESOURCE, isTSAChecker = true)

        val options =
            TvmOptions(
                intercontractOptions = IntercontractOptions(communicationScheme = null),
                turnOnTLBParsingChecks = false,
            )

        val contracts = listOf(checkerCode, sortContractCode)
        val result =
            analyzeInterContract(
                contracts,
                startContractId = 0, // Checker contract is the first to analyze
                methodId = TvmContext.RECEIVE_INTERNAL_ID,
                options = options,
            )

        val failure =
            result.tests.single {
                (it.result as? TvmTestFailure)?.failure?.exit?.exitCode == 256
            }
        val firstValue = failure.fetchedValues[0] as TvmTestIntegerValue
        val secondValue = failure.fetchedValues[1] as TvmTestIntegerValue

        assertTrue(
            firstValue.value > secondValue.value,
            "First value ${firstValue.value} is expected to be greater than second value ${secondValue.value}",
        )
    }
}
