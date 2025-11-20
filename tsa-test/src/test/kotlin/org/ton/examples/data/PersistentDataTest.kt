package org.ton.examples.data

import org.ton.cell.CellBuilder
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.getResourcePath
import org.usvm.test.resolver.TvmTestFailure
import kotlin.test.Test
import kotlin.test.assertTrue

class PersistentDataTest {
    @Test
    fun testWithoutConcreteData() {
        val path = getResourcePath<PersistentDataTest>("/data/data.fc")

        val symbolicResult = funcCompileAndAnalyzeAllMethods(path)
        val allTests = symbolicResult.map { it.tests }.flatten()
        val results = allTests.map { it.result }
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { (it as? TvmTestFailure)?.exitCode == 1000 })
    }

    @Test
    fun testWithConcreteData() {
        val cell = CellBuilder().storeInt(100, 32).endCell()

        val path = getResourcePath<PersistentDataTest>("/data/data.fc")

        val symbolicResult =
            funcCompileAndAnalyzeAllMethods(path, concreteContractData = TvmConcreteContractData(contractC4 = cell))
        val allTests = symbolicResult.map { it.tests }.flatten()
        val results = allTests.map { it.result }
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { (it as? TvmTestFailure)?.exitCode != 1000 })
    }
}
