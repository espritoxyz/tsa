package org.ton

/**
 * [parameterInfos] in [TvmInputInfo] maps parameter indices to their [TvmParameterInfo].
 * Parameters are indexed from the end.
 * For example, the last parameter of a function always has index 0.
 * */
data class TvmInputInfo(
    val parameterInfos: Map<Int, TvmParameterInfo> = emptyMap(),
    val c4Info: List<TlbCompositeLabel>? = null,
)
