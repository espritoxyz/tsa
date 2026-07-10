package org.usvm.machine.interpreter

import kotlinx.serialization.Serializable
import org.usvm.test.resolver.TvmTestAuthValue

@Serializable
sealed interface AuthAnalysisResult {
    /**
     * Means "We failed to retrieve any information"
     */
    data object Unknown : AuthAnalysisResult

    /**
     * Means "We did not run an authorization analysis on this state"
     */
    data object NotCollected : AuthAnalysisResult

    data class Collected(
        val authorizedEntities: List<TvmTestAuthValue>,
    ) : AuthAnalysisResult
}
