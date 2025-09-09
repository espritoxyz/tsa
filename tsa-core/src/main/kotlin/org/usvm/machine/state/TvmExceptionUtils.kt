package org.usvm.machine.state

import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager

fun checkOutOfRange(
    notOutOfRangeExpr: UBoolExpr,
    scope: TvmStepScopeManager
): Unit? =
    scope.fork(
        condition = notOutOfRangeExpr,
        falseStateIsExceptional = true,
        blockOnFalseState = {
            ctx.throwIntegerOutOfRangeError(this)
        }
    )

fun checkOutOfRange(
    expr: UExpr<TvmInt257Sort>,
    scope: TvmStepScopeManager,
    min: Int,
    max: Int
) = scope.doWithCtx {
    val cond = mkBvSignedLessOrEqualExpr(min.toBv257(), expr) and mkBvSignedLessOrEqualExpr(expr, max.toBv257())

    scope.fork(
        cond,
        falseStateIsExceptional = true,
        blockOnFalseState = throwIntegerOutOfRangeError
    )
}

fun checkOverflow(
    noOverflowExpr: UBoolExpr,
    scope: TvmStepScopeManager
): Unit? =
    scope.fork(
        noOverflowExpr,
        falseStateIsExceptional = true,
        blockOnFalseState = { ctx.throwIntegerOverflowError(this) }
    )

fun checkUnderflow(
    noUnderflowExpr: UBoolExpr,
    scope: TvmStepScopeManager
): Unit? =
    scope.fork(
        noUnderflowExpr,
        falseStateIsExceptional = true,
        blockOnFalseState = { ctx.throwIntegerOverflowError(this) }
    )
