package org.usvm.machine.state

import org.usvm.UConcreteHeapRef

data class TvmRefEmptyValue(
    val emptyCell: UConcreteHeapRef,
    val emptySlice: UConcreteHeapRef,
    val emptyBuilder: UConcreteHeapRef,
)
