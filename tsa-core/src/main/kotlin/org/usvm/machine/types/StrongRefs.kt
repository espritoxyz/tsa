package org.usvm.machine.types

import org.usvm.UHeapRef

data class SliceGeneralRef<out T : UHeapRef>(
    val value: T,
)

typealias SliceRef = SliceGeneralRef<UHeapRef>
