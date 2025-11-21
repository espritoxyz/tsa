package org.ton.benchmark

import kotlinx.serialization.Serializable
import org.usvm.test.resolver.TvmMethodCoverage
import kotlin.time.Duration

@Serializable
data class BenchmarkRunResult(
    val name: String,
    val methodsCoverage: Map<Long, TvmMethodCoverage>,
    val fullTime: Duration,
) {
    companion object {
        fun empty(name: String = "empty"): BenchmarkRunResult =
            BenchmarkRunResult(name, methodsCoverage = emptyMap(), fullTime = Duration.ZERO)
    }
}
