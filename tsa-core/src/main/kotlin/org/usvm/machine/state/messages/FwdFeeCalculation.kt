package org.usvm.machine.state.messages

import org.ton.cell.Cell
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TvmContext
import org.usvm.machine.state.BIT_PRICE
import org.usvm.machine.state.CELL_PRICE
import org.usvm.machine.state.FIRST_FRAC
import org.usvm.machine.state.LUMP_PRICE
import org.usvm.test.resolver.TvmTestCellValue
import org.usvm.test.resolver.transformTestCellIntoCell

data class FwdFeeInfo(
    val symbolicFwdFee: UExpr<TvmContext.TvmInt257Sort>,
    val stateInitRef: UHeapRef?, // null if inlined
    val msgBodyRef: UHeapRef?, // null if inlined
)

fun calculateConcreteForwardFee(
    stateInit: TvmTestCellValue?,
    msgBody: TvmTestCellValue?,
): Long {
    val stateInitCell = stateInit?.let { transformTestCellIntoCell(stateInit) }
    val msgBodyCell = msgBody?.let { transformTestCellIntoCell(msgBody) }

    val cells = mutableListOf<Cell>()
    if (stateInitCell != null) {
        cells += stateInitCell
    }
    if (msgBodyCell != null) {
        cells += msgBodyCell
    }

    val bits = calculateNumberOfBitsInUniqueCells(cells)
    val uniqueCells = calculateNumberOfUniqueCells(cells)

    val fullFwdFee = LUMP_PRICE + (bits * BIT_PRICE + uniqueCells * CELL_PRICE + (1 shl 16) - 1) / (1 shl 16)
    return calculateTwoThirdLikeInTVM(fullFwdFee)
}

private fun calculateTwoThirdLikeInTVM(value: Long): Long {
    check(value >= 0) {
        "Cannot perform this operation for negative integer"
    }
    return value - ((value * FIRST_FRAC) shr 16)
}

private fun calculateNumberOfBitsInUniqueCells(cells: List<Cell>): Int {
    val hashes = hashSetOf<String>()
    var result = 0
    cells.forEach {
        visitUniqueCells(it, hashes) { cell ->
            result += cell.bits.size
        }
    }
    return result
}

private fun calculateNumberOfUniqueCells(cells: List<Cell>): Int {
    val hashes = hashSetOf<String>()
    cells.forEach {
        visitUniqueCells(it, hashes) {}
    }
    return hashes.size
}

private fun visitUniqueCells(
    cell: Cell,
    visited: MutableSet<String>,
    onCell: (Cell) -> Unit,
) {
    val curHash = cell.hash().toHex()
    if (curHash in visited) {
        return
    }
    onCell(cell)
    visited.add(curHash)
    cell.refs.forEach {
        visitUniqueCells(it, visited, onCell)
    }
}
