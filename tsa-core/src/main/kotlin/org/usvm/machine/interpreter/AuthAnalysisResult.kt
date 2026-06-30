package org.usvm.machine.interpreter

import kotlinx.serialization.Serializable
import org.usvm.test.resolver.TvmTestAuthValue

@Serializable
sealed interface AuthAnalysisResult {
    data object Unknown : AuthAnalysisResult

    data object NotCollected : AuthAnalysisResult

    data class Collected(
        val authorizedEntities: List<TvmTestAuthValue>,
    ) : AuthAnalysisResult
}
