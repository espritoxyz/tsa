package org.usvm.machine.state.input

sealed interface TvmInput

data object TvmStackInput : TvmInput
