package org.usvm.test.resolver

/**
 * @return remaining data bits and refs as a cell
 */
fun truncateSliceCell(slice: TvmTestSliceValue): TvmTestDataCellValue {
    val truncatedCellData = slice.cell.data.drop(slice.dataPos)
    val truncatedCellRefs = slice.cell.refs.drop(slice.refPos)

    // TODO handle cell type loads
    return TvmTestDataCellValue(truncatedCellData, truncatedCellRefs)
}

fun TvmTestBuilderValue.endCell(): TvmTestDataCellValue = TvmTestDataCellValue(data, refs)
