package org.usvm.machine.fields

import org.usvm.machine.TvmContext

class TvmFieldManagers(
    private val ctx: TvmContext,
    val cellDataFieldManager: TvmCellDataFieldManager = TvmCellDataFieldManager(ctx),
    val cellDataLengthFieldManager: TvmCellDataLengthFieldManager = TvmCellDataLengthFieldManager(),
) {
    fun clone() =
        TvmFieldManagers(
            ctx,
            cellDataFieldManager.clone(),
            cellDataLengthFieldManager.clone(),
        )
}
