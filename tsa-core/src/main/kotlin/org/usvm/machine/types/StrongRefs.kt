package org.usvm.machine.types

import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef

/**
 * Strong type wrapper for refs that hold a value of **slice** type.
 */
@JvmInline
value class SliceGeneralRef<out T : UHeapRef>(
    val value: T,
)

/**
 * Strong type wrapper for refs that hold a value of **cell** type.
 */
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
