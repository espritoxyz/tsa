package org.usvm.machine.state.hash

import org.ton.cell.Cell
import org.usvm.test.resolver.TvmTestBuilderValue
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestDictCellValue
import org.usvm.test.resolver.TvmTestReferenceValue
import org.usvm.test.resolver.TvmTestSliceValue
import org.usvm.test.resolver.transformTestDataCellIntoCell
import org.usvm.test.resolver.transformTestDictCellIntoCell
import org.usvm.test.resolver.truncateSliceCell
import java.math.BigInteger

fun calculateConcreteHash(value: TvmTestReferenceValue): BigInteger =
    when (value) {
        is TvmTestDataCellValue -> {
            val cell = transformTestDataCellIntoCell(value)
            calculateHashOfCell(cell)
        }

        is TvmTestDictCellValue -> {
            val cell = transformTestDictCellIntoCell(value)
            calculateHashOfCell(cell)
        }

        is TvmTestBuilderValue -> {
            val cell = transformTestDataCellIntoCell(value.toCell())
            calculateHashOfCell(cell)
        }

        is TvmTestSliceValue -> {
            val restCell = truncateSliceCell(value)
            calculateConcreteHash(restCell)
        }
    }

fun calculateHashOfCell(cell: Cell): BigInteger = BigInteger(ByteArray(1) { 0 } + cell.hash().toByteArray())
