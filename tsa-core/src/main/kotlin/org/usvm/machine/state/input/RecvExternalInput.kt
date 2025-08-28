package org.usvm.machine.state.input

import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.machine.TvmContext

class RecvExternalInput : ReceiverInput {
    override val msgValue: UExpr<TvmContext.TvmInt257Sort>
        get() = TODO("Not yet implemented")
    override val msgBodySliceNonBounced: UConcreteHeapRef
        get() = TODO("Not yet implemented")
}
