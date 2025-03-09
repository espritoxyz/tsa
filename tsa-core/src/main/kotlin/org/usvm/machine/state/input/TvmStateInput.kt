package org.usvm.machine.state.input

sealed interface TvmStateInput

data object TvmStateStackInput : TvmStateInput
