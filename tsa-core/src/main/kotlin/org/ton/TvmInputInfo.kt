package org.ton

/**
 * [parameterInfos] in [TvmInputInfo] maps parameter indices to their [TvmParameterInfo].
 * Parameters are indexed from the end.
 * For example, the last parameter of a function always has index 0.
 * */
@JvmInline
value class TvmInputInfo(
    val parameterInfos: Map<Int, TvmParameterInfo> = emptyMap(),
)
