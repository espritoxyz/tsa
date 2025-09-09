package org.usvm.machine.types

import kotlinx.serialization.Serializable
import org.ton.TlbBuiltinLabel
import org.usvm.UExpr
import org.usvm.machine.TvmSizeSort

@Serializable
sealed interface TvmStructuralExit<out DataCellType, out ExpectedCellType : TlbBuiltinLabel> {
    val ruleId: String
}

data class TvmUnexpectedDataReading<DataCellType>(
    val readingType: DataCellType,
) : TvmStructuralExit<DataCellType, Nothing> {
    override val ruleId: String
        get() = "unexpected-data-reading"

    override fun toString() = "Unexpected reading of $readingType: slice should have no data left."
}

data class TvmReadingOutOfSwitchBounds<DataCellType>(
    val readingType: DataCellType,
) : TvmStructuralExit<DataCellType, Nothing> {
    override val ruleId: String
        get() = "out-of-switch-bounds"

    override fun toString() = "Reading of $readingType is out of switch bounds"
}

data class TvmReadingSwitchWithUnexpectedType<DataCellType>(
    val readingType: DataCellType,
) : TvmStructuralExit<DataCellType, Nothing> {
    override val ruleId: String
        get() = "unexpected-type-for-switch"

    override fun toString() = "Reading switch with $readingType is forbidden"
}

object TvmUnexpectedRefReading : TvmStructuralExit<Nothing, Nothing> {
    override val ruleId: String
        get() = "unexpected-ref-reading"

    override fun toString() = "Unexpected reading of a reference: slice should have no references left."
}

object TvmUnexpectedEndOfReading : TvmStructuralExit<Nothing, Nothing> {
    override val ruleId: String
        get() = "unexpected-end-of-cell"

    override fun toString() = "Unexpected end of reading: slice is not supposed to be empty"
}

data class TvmReadingOfUnexpectedType<DataCellType, ExpectedCellType : TlbBuiltinLabel>(
    val expectedLabel: ExpectedCellType,
    val typeArgs: List<UExpr<TvmSizeSort>>,
    val actualType: DataCellType,
) : TvmStructuralExit<DataCellType, ExpectedCellType> {
    override val ruleId: String
        get() = "unexpected-cell-type"

    override fun toString() = "Reading of unexpected type: expected reading of $expectedLabel, but read $actualType"
}
