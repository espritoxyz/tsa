package org.usvm.machine.types

import io.ksmt.sort.KSort
import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.machine.TvmContext

sealed interface TvmCellDataTypeReadValue

class UExprReadResult<Sort : KSort>(
    val expr: UExpr<Sort>,
) : TvmCellDataTypeReadValue

class UExprPairReadResult<Sort1 : KSort, Sort2 : KSort>(
    val first: UExpr<Sort1>,
    val second: UExpr<Sort2>,
) : TvmCellDataTypeReadValue

fun <ReadResult : TvmCellDataTypeReadValue> mkIte(
    ctx: TvmContext,
    condition: UBoolExpr,
    trueBranch: ReadResult,
    falseBranch: ReadResult,
): ReadResult =
    when (trueBranch) {
        is UExprReadResult<*> -> {
            mkUExprIte<KSort>(ctx, condition, trueBranch.uncheckedCast(), falseBranch.uncheckedCast()).uncheckedCast()
        }

        is UExprPairReadResult<*, *> -> {
            mkUExprPairIte<KSort, KSort>(
                ctx,
                condition,
                trueBranch.uncheckedCast(),
                falseBranch.uncheckedCast(),
            ).uncheckedCast()
        }
    }

fun <Sort : KSort> mkUExprIte(
    ctx: TvmContext,
    condition: UBoolExpr,
    trueBranch: UExprReadResult<Sort>,
    falseBranch: UExprReadResult<Sort>,
): UExprReadResult<Sort> {
    val expr = ctx.mkIte(condition, trueBranch = { trueBranch.expr }, falseBranch = { falseBranch.expr })
    return UExprReadResult(expr)
}

fun <Sort1 : KSort, Sort2 : KSort> mkUExprPairIte(
    ctx: TvmContext,
    condition: UBoolExpr,
    trueBranch: UExprPairReadResult<Sort1, Sort2>,
    falseBranch: UExprPairReadResult<Sort1, Sort2>,
): UExprPairReadResult<Sort1, Sort2> {
    val expr1 = ctx.mkIte(condition, trueBranch = { trueBranch.first }, falseBranch = { falseBranch.first })
    val expr2 = ctx.mkIte(condition, trueBranch = { trueBranch.second }, falseBranch = { falseBranch.second })
    return UExprPairReadResult(expr1, expr2)
}
