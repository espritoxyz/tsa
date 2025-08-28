package org.usvm.machine.state.input

import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TvmContext

sealed interface ReceiverInput : TvmInput {
    val msgValue: UExpr<TvmContext.TvmInt257Sort>
    val msgBodySliceNonBounced: UConcreteHeapRef
    val srcAddressCell: UConcreteHeapRef?  // null for external messages
}
