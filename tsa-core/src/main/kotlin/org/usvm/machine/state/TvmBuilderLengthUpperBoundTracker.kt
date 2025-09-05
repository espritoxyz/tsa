package org.usvm.machine.state

import kotlinx.collections.immutable.PersistentMap
import org.usvm.UConcreteHeapRef

@JvmInline
value class TvmBuilderLengthUpperBoundTracker(
    val builderRefToLengthUpperBound: PersistentMap<UConcreteHeapRef, Int>,
)
