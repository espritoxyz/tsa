package org.usvm.machine.intblast

import io.ksmt.KContext
import io.ksmt.expr.KBvAndExpr
import io.ksmt.expr.KBvArithShiftRightExpr
import io.ksmt.expr.KBvDivNoOverflowExpr
import io.ksmt.expr.KBvLogicalShiftRightExpr
import io.ksmt.expr.KBvMulExpr
import io.ksmt.expr.KBvMulNoOverflowExpr
import io.ksmt.expr.KBvMulNoUnderflowExpr
import io.ksmt.expr.KBvNAndExpr
import io.ksmt.expr.KBvNorExpr
import io.ksmt.expr.KBvOrExpr
import io.ksmt.expr.KBvShiftLeftExpr
import io.ksmt.expr.KBvSignedDivExpr
import io.ksmt.expr.KBvSignedModExpr
import io.ksmt.expr.KBvSignedRemExpr
import io.ksmt.expr.KBvUnsignedDivExpr
import io.ksmt.expr.KBvUnsignedRemExpr
import io.ksmt.expr.KBvXNorExpr
import io.ksmt.expr.KBvXorExpr
import io.ksmt.expr.KExpr
import io.ksmt.expr.KInterpretedValue
import io.ksmt.expr.transformer.KExprVisitResult
import io.ksmt.expr.transformer.KNonRecursiveVisitor
import io.ksmt.sort.KBvSort
import io.ksmt.sort.KSort

class Bv2IntExprFilter(
    ctx: KContext,
    private val excludeNonConstShift: Boolean = true,
    private val excludeNonConstBvand: Boolean = true,
    private val excludeNonlinearArith: Boolean = false
) : KNonRecursiveVisitor<Boolean>(ctx) {
    private inline fun filter(
        enabled: Boolean,
        body: () -> Boolean
    ): Boolean = if (enabled) body() else true

    private fun filterNonConstBitwiseOp(
        lhs: KExpr<*>,
        rhs: KExpr<*>
    ) = filter(excludeNonConstBvand) {
        lhs is KInterpretedValue || rhs is KInterpretedValue
    }

    @Suppress("UnusedPrivateMember")
    private fun filterNonConstShift(
        arg: KExpr<*>,
        shift: KExpr<*>
    ) = filter(excludeNonConstShift) {
        shift is KInterpretedValue
    }

    private fun filterNonlinearArith(
        lhs: KExpr<*>,
        rhs: KExpr<*>
    ) = filter(excludeNonlinearArith) {
        lhs is KInterpretedValue || rhs is KInterpretedValue
    }

    override fun <T : KSort> defaultValue(expr: KExpr<T>): Boolean = true

    override fun mergeResults(
        left: Boolean,
        right: Boolean
    ): Boolean = left && right

    override fun <T : KBvSort> visit(expr: KBvAndExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonConstBitwiseOp(expr.arg0, expr.arg1)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvOrExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonConstBitwiseOp(expr.arg0, expr.arg1)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvXorExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonConstBitwiseOp(expr.arg0, expr.arg1)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvNAndExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonConstBitwiseOp(expr.arg0, expr.arg1)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvNorExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonConstBitwiseOp(expr.arg0, expr.arg1)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvXNorExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonConstBitwiseOp(expr.arg0, expr.arg1)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvShiftLeftExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonConstShift(expr.arg, expr.shift)) return saveVisitResult(expr, false)
        if (!filterNonlinearArith(expr.arg, expr.shift)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvLogicalShiftRightExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonConstShift(expr.arg, expr.shift)) return saveVisitResult(expr, false)
        if (!filterNonlinearArith(expr.arg, expr.shift)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvArithShiftRightExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonConstShift(expr.arg, expr.shift)) return saveVisitResult(expr, false)
        if (!filterNonlinearArith(expr.arg, expr.shift)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvMulExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonlinearArith(expr.arg0, expr.arg1)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvMulNoOverflowExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonlinearArith(expr.arg0, expr.arg1)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvMulNoUnderflowExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonlinearArith(expr.arg0, expr.arg1)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvSignedDivExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonlinearArith(expr.arg0, expr.arg1)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvUnsignedDivExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonlinearArith(expr.arg0, expr.arg1)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvDivNoOverflowExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonlinearArith(expr.arg0, expr.arg1)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvSignedModExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonlinearArith(expr.arg0, expr.arg1)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvSignedRemExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonlinearArith(expr.arg0, expr.arg1)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }

    override fun <T : KBvSort> visit(expr: KBvUnsignedRemExpr<T>): KExprVisitResult<Boolean> {
        if (!filterNonlinearArith(expr.arg0, expr.arg1)) return saveVisitResult(expr, false)
        return super.visit(expr)
    }
}
