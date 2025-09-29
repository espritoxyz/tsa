package org.usvm.machine.state

import io.ksmt.sort.KBvSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TvmContext.TvmCellDataSort
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmSizeSort
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeSubExpr

/**
 * This should be used only in core TL-B logic!
 *
 * @return bv 1023 with undefined high-order bits
 */
fun TvmState.preloadDataBitsFromCellWithoutStructuralAsserts(
    cell: UHeapRef,
    offset: UExpr<TvmSizeSort>,
    sizeBits: UExpr<TvmSizeSort>,
): UExpr<TvmCellDataSort> =
    with(ctx) {
        val cellData =
            fieldManagers
                .cellDataFieldManager
                .readCellDataWithoutAsserts(this@preloadDataBitsFromCellWithoutStructuralAsserts, cell)
        val endOffset = mkSizeAddExpr(offset, sizeBits)
        val offsetDataPos = mkSizeSubExpr(maxDataLengthSizeExpr, endOffset)
        mkBvLogicalShiftRightExpr(cellData, offsetDataPos.zeroExtendToSort(cellDataSort))
    }

/**
 * This should be used only in core TL-B logic!
 * */
fun TvmState.preloadDataBitsFromCellWithoutStructuralAsserts(
    cell: UHeapRef,
    offset: UExpr<TvmSizeSort>,
    sizeBits: Int,
): UExpr<KBvSort> =
    with(ctx) {
        val shiftedData = preloadDataBitsFromCellWithoutStructuralAsserts(cell, offset, mkSizeExpr(sizeBits))
        mkBvExtractExpr(high = sizeBits - 1, low = 0, shiftedData)
    }

/**
 * This should be used only in core TL-B logic!
 * */
fun TvmState.loadIntFromCellWithoutChecksAndStructuralAsserts(
    cell: UHeapRef,
    offset: UExpr<TvmSizeSort>,
    sizeBits: UExpr<TvmSizeSort>,
    isSigned: Boolean,
): UExpr<TvmInt257Sort> =
    with(ctx) {
        val shiftedData = preloadDataBitsFromCellWithoutStructuralAsserts(cell, offset, sizeBits.extractToSizeSort())
        extractIntFromShiftedData(shiftedData, sizeBits, isSigned)
    }
