package org.usvm.machine.fields

import org.ton.bytecode.TvmField
import org.ton.bytecode.TvmFieldImpl
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.state.TvmState
import org.usvm.machine.types.TvmCellType

data object TvmCellRefsLengthFieldManager {
    private val cellRefsLengthField: TvmField = TvmFieldImpl(TvmCellType, "refsLength")

    fun readCellRefs(state: TvmState, cellRef: UHeapRef): UExpr<TvmSizeSort> {
        TODO()
    }
}
