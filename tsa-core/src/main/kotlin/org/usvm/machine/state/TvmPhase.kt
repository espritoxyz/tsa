package org.usvm.machine.state

sealed interface TvmPhase

data object TvmComputePhase : TvmPhase

data class TvmActionPhase(
    val computePhaseResult: TvmResult.TvmTerminalResult,
) : TvmPhase

data class TvmBouncePhase(
    val computePhaseResult: TvmResult.TvmTerminalResult,
    val actionPhaseResult: TvmResult.TvmTerminalResult?,
) : TvmPhase

data class TvmExitPhase(
    val computePhaseResult: TvmResult.TvmTerminalResult,
    val actionPhaseResult: TvmResult.TvmTerminalResult?,
) : TvmPhase

data object TvmPostProcessPhase : TvmPhase

data object TvmTerminated : TvmPhase
