package org.ton.benchmark

fun interface SingleFileBenchmark {
    fun run(): BenchmarkRunResult
}

fun SingleFileBenchmark.withWarmup(): SingleFileBenchmark =
    SingleFileBenchmark {
        run()
    }
