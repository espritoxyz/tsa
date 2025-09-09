package org.usvm.machine.types

import io.ksmt.sort.KBvSort
import org.ton.Endian
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort

sealed interface TvmCellDataTypeRead<ReadResult : TvmCellDataTypeReadValue>

sealed interface SizedCellDataTypeRead {
    val sizeBits: UExpr<TvmSizeSort>
}

data class TvmCellDataIntegerRead(
    override val sizeBits: UExpr<TvmSizeSort>,
    val isSigned: Boolean,
    val endian: Endian,
) : TvmCellDataTypeRead<UExprReadResult<TvmContext.TvmInt257Sort>>,
    SizedCellDataTypeRead

class TvmCellMaybeConstructorBitRead(
    val ctx: TvmContext,
) : TvmCellDataTypeRead<UExprReadResult<UBoolSort>>,
    SizedCellDataTypeRead {
    override val sizeBits: UExpr<TvmSizeSort>
        get() = ctx.oneSizeExpr
}

// As a read result expects address length + slice with the address
class TvmCellDataMsgAddrRead(
    val ctx: TvmContext,
) : TvmCellDataTypeRead<UExprPairReadResult<TvmSizeSort, UAddressSort>>

data class TvmCellDataBitArrayRead(
    override val sizeBits: UExpr<TvmSizeSort>,
) : TvmCellDataTypeRead<UExprReadResult<UAddressSort>>,
    SizedCellDataTypeRead

// As a read result expects bitvector of size 4 (coin prefix) + coin value as int257
class TvmCellDataCoinsRead(
    val ctx: TvmContext,
) : TvmCellDataTypeRead<UExprPairReadResult<KBvSort, TvmContext.TvmInt257Sort>>

fun <ReadResult : TvmCellDataTypeReadValue> TvmCellDataTypeRead<ReadResult>.isEmptyRead(ctx: TvmContext): UBoolExpr =
    with(ctx) {
        when (this@isEmptyRead) {
            is SizedCellDataTypeRead -> sizeBits eq zeroSizeExpr
            is TvmCellDataMsgAddrRead, is TvmCellDataCoinsRead -> falseExpr
        }
    }
