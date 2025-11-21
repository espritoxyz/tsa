package org.ton.benchmark

import org.ton.cell.CellBuilder
import org.usvm.machine.FuncAnalyzer
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmConcreteGeneralData
import org.usvm.machine.TvmOptions
import org.usvm.machine.getResourcePath
import java.nio.file.Path
import kotlin.time.measureTimedValue

const val FIFT_STDLIB_PATH = "/fiftstdlib"

val FIFT_STDLIB_RESOURCE: Path = getResourcePath(object {}.javaClass, FIFT_STDLIB_PATH)
val funcAnalyzer = FuncAnalyzer(fiftStdlibPath = FIFT_STDLIB_RESOURCE)

val testOptionsToAnalyzeSpecificMethod = TvmOptions(useReceiverInputs = false)

object DictBenchmark {
    private val hardIteration: String = "/dict/dict-hard-iteration.fc"

    fun hardInputDictIteration(iterCount: Int) =
        SingleFileBenchmark {
            val resourcePath = getResourcePath<DictBenchmark>(hardIteration)
            val (symbolicResult, duration) =
                measureTimedValue {
                    funcAnalyzer.analyzeAllMethods(
                        resourcePath,
                        TvmConcreteGeneralData(),
                        TvmConcreteContractData(CellBuilder().storeInt(iterCount, 64).endCell()),
                        hashSetOf(),
                        null,
                        emptyMap(),
                        tvmOptions = testOptionsToAnalyzeSpecificMethod.copy(loopIterationLimit = 1000),
                    )
                }
            val tests = symbolicResult.testSuites.single()
            val methodsCoverage =
                mapOf(
                    tests.methodId.toLong() to tests.methodCoverage,
                )
            BenchmarkRunResult(
                "dict-iteration-$iterCount",
                methodsCoverage,
                duration,
            )
        }
}
