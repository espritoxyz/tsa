package org.ton.benchmark

import org.ton.TvmInputInfo
import org.ton.bytecode.MethodId
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmConcreteGeneralData
import org.usvm.machine.TvmOptions
import org.usvm.machine.getResourcePath

object WarmUp {
    private val dictExamplePath: String = "/dict/dict_examples.fc"

    fun runWarmUp() {
        val resourcePath = getResourcePath<DictBenchmark>(dictExamplePath)
        val symbolicResult =
            funcAnalyzer.analyzeAllMethods(
                resourcePath,
                TvmConcreteGeneralData(),
                TvmConcreteContractData(),
                hashSetOf<MethodId>(),
                null,
                emptyMap<MethodId, TvmInputInfo>(),
                TvmOptions(),
            )
        println("Warmup ended with tests: ${symbolicResult.size}")
    }
}
