package org.ton.examples.data

import org.ton.boc.BagOfCells
import org.ton.cell.CellBuilder
import org.ton.examples.funcCompileAndAnalyzeAllMethods
import org.usvm.machine.getResourcePath
import org.usvm.test.resolver.TvmMethodFailure
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
        assertTrue(results.any { (it as? TvmMethodFailure)?.exitCode == 1000 })
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testWithConcreteData() {

        val cell = CellBuilder().storeInt(100, 32).endCell()
        val cellHex = BagOfCells(cell).toByteArray().toHexString()

        val path = getResourcePath<PersistentDataTest>("/data/data.fc")

        val symbolicResult = funcCompileAndAnalyzeAllMethods(path, contractDataHex = cellHex)
        val allTests = symbolicResult.map { it.tests }.flatten()
        val results = allTests.map { it.result }
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { (it as? TvmMethodFailure)?.exitCode != 1000 })
    }
}