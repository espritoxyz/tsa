package org.ton.examples.hash

import org.ton.cell.buildCell
import org.ton.examples.args.ArgsConstraintsTest
import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.utils.TvmTestExecutor
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmOptions
import org.usvm.machine.getResourcePath
import kotlin.test.Test

class ExoticHashCalculation {
    val test = "/hash/hash-from-c4.fc"

    @Test
    fun `reference cell hash`() {
        val path = getResourcePath<ArgsConstraintsTest>(test)
        val data =
            buildCell {
                isExotic = true
                storeBits("00000010".map { it == '1' })
                storeBits((0..<32).flatMap { "00000001".map { it == '1' } })
                refs.forEach { storeRef(it) }
            }

        val result =
            funcCompileAndAnalyzeAllMethods(
                path,
                tvmOptions = TvmOptions(analyzeBouncedMessaged = true),
                concreteContractData = TvmConcreteContractData(contractC4 = data),
            )
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }
}
