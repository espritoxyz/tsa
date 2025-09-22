package org.usvm.machine.fields

import org.usvm.machine.TvmContext

class TvmFieldManagers(
    private val ctx: TvmContext,
    val cellDataFieldManager: TvmCellDataFieldManager = TvmCellDataFieldManager(ctx),
    val cellDataLengthFieldManager: TvmCellDataLengthFieldManager = TvmCellDataLengthFieldManager(),
) {
    // store here for consistency with other managers
    val cellRefsLengthFieldManager = TvmCellRefsLengthFieldManager

    fun clone() =
        TvmFieldManagers(
            ctx,
            cellDataFieldManager.clone(),
            cellDataLengthFieldManager.clone(),
        )
}
