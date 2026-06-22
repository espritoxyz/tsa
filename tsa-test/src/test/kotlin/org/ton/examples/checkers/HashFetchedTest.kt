package org.ton.examples.checkers

import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.test.utils.assertInvariantHolds
import org.ton.test.utils.assertPropertiesFound
import org.ton.test.utils.extractCheckerContractFromResource
import org.ton.test.utils.extractFuncContractFromResource
import org.ton.test.utils.hasExitCode
import org.usvm.machine.analyzeInterContract
import org.usvm.machine.toMethodId
import org.usvm.test.resolver.TvmTestIntegerValue
import java.math.BigInteger
import kotlin.test.Test

class HashFetchedTest {
    @Test
    fun `hash fetched tests`() {
        val checker = extractCheckerContractFromResource("/checkers/fetch-hash/checker.fc")
        val contract = extractFuncContractFromResource("/checkers/fetch-hash/contract.fc")
        val tests =
            analyzeInterContract(
                contracts = listOf(checker, contract),
                startContractId = 0,
                methodId = 0.toMethodId(),
            )
        val cell = CellBuilder().storeUInt(2, 64).endCell()
        val expected = calculateCellHash(cell)
        tests.assertPropertiesFound(hasExitCode(1000))
        tests.filter { hasExitCode(1000)(it) }.assertInvariantHolds {
            (it.fetchedValues[0] as? TvmTestIntegerValue)?.value == expected
        }
    }

    private fun calculateCellHash(cell: Cell): BigInteger {
        val expected = BigInteger(cell.hash().toByteArray())
        return expected
    }
}
