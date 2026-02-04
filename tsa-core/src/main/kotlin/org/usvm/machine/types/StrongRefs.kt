package org.usvm.machine.types

import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef

@JvmInline
value class SliceGeneralRef<out T : UHeapRef>(
    val value: T,
)

@JvmInline
value class CellGeneralRef<out T : UHeapRef>(
    val value: T,
)

typealias SliceRef = SliceGeneralRef<UHeapRef>
typealias ConcreteSliceRef = SliceGeneralRef<UConcreteHeapRef>
typealias CellRef = CellGeneralRef<UHeapRef>
typealias ConcreteCellRef = CellGeneralRef<UConcreteHeapRef>

fun UConcreteHeapRef.asSliceRef(): ConcreteSliceRef = ConcreteSliceRef(this)

fun UHeapRef.asSliceRef(): SliceRef = SliceRef(this)

fun UConcreteHeapRef.asCellRef(): ConcreteCellRef = ConcreteCellRef(this)

fun UHeapRef.asCellRef(): CellRef = CellRef(this)
