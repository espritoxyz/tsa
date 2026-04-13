package org.usvm.machine.state

import org.usvm.UHeapRef
import org.usvm.machine.Int257Expr

/**
 * @param distinctCells x
 * @param dataBits y
 * @param cellRefs z
 * @param maximumCellCount a limit from the DATASIZEQ function.
 * When the analyzed cell has in its tree more cells than specified by this bound, the instruction returns an error value.
 */
data class DataSizeInfo(
    val analyzedCell: UHeapRef,
    val maximumCellCount: Int257Expr,
    val hasEnoughMaxCellCount: Boolean,
    val distinctCells: Int257Expr,
    val dataBits: Int257Expr,
    val cellRefs: Int257Expr,
)
