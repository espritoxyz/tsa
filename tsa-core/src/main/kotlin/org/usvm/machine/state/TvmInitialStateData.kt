package org.usvm.machine.state

import org.usvm.UHeapRef
import org.usvm.machine.state.TvmStack.TvmStackTupleValueConcreteNew

data class TvmInitialStateData(
    val persistentData: UHeapRef,
    val firstElementOfC7: TvmStackTupleValueConcreteNew,
)
