package org.ton.benchmark

import java.io.File

fun main() {
    WarmUp.runWarmUp()
    val benchmarks =
        listOf(3, 4, 5, 6, 7, 8, 9, 10).map {
            val result = DictBenchmark.hardInputDictIteration(it).withWarmup().run()
            println("Ran $it")
            result
        }
    val reported = BenchmarkReporter()
    val text =
        reported.generateComparisonMarkdown(
            benchmarks,
            listOf(BenchmarkRunResult.empty()),
            printCoverage = false,
        )
    val file = File("dicts.md")
    file.writeText(text)
    println("Output written to ${file.toURI()}")
}
