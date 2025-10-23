package org.usvm.machine.fields

import kotlinx.collections.immutable.PersistentMap
import org.usvm.UConcreteHeapRef

@JvmInline
value class TvmBuilderLengthUpperBoundTracker(
    val builderRefToLengthUpperBound: PersistentMap<UConcreteHeapRef, Int>,
) {
    fun setUpperBound(
        ref: UConcreteHeapRef,
        bound: Int?,
    ) = TvmBuilderLengthUpperBoundTracker(
        if (bound != null) {
            builderRefToLengthUpperBound.put(ref, bound)
        } else {
            builderRefToLengthUpperBound.remove(ref)
        },
    )
}
