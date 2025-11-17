package org.usvm.machine.interpreter.inputdict

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import org.usvm.UBoolExpr
import org.usvm.machine.TvmContext

internal data class ConstraintBuilder(
    private val ctx: TvmContext,
    private val constraints: MutableList<UBoolExpr> = mutableListOf(),
) {
    fun build(): PersistentList<UBoolExpr> = constraints.toPersistentList()

    fun addCs(cs: TvmContext.() -> UBoolExpr) {
        constraints += cs(ctx)
    }
}

enum class CmpKind { LE, LT, GE, GT }

data class Cmp(
    val kind: CmpKind,
    val isSigned: Boolean = false,
) {
    constructor(isLess: Boolean, isStrict: Boolean, isSigned: Boolean = false) : this(
        when {
            isLess && isStrict -> CmpKind.LT
            isLess && !isStrict -> CmpKind.LE
            !isLess && isStrict -> CmpKind.GT
            !isLess && !isStrict -> CmpKind.GE
            else -> error("unreachable")
        },
        isSigned,
    )

    fun createCmp(ctx: TvmContext): (ExtendedDictKey, ExtendedDictKey) -> UBoolExpr =
        when (kind) {
            CmpKind.LE ->
                if (isSigned) {
                    { a, b -> ctx.mkBvSignedLessOrEqualExpr(a, b) }
                } else {
                    { a, b -> ctx.mkBvUnsignedLessOrEqualExpr(a, b) }
                }

            CmpKind.LT ->
                if (isSigned) {
                    { a, b -> ctx.mkBvSignedLessExpr(a, b) }
                } else {
                    { a, b -> ctx.mkBvUnsignedLessExpr(a, b) }
                }

            CmpKind.GE ->
                if (isSigned) {
                    { a, b -> ctx.mkBvSignedGreaterOrEqualExpr(a, b) }
                } else {
                    { a, b -> ctx.mkBvUnsignedGreaterOrEqualExpr(a, b) }
                }

            CmpKind.GT ->
                if (isSigned) {
                    { a, b -> ctx.mkBvSignedGreaterExpr(a, b) }
                } else {
                    { a, b -> ctx.mkBvUnsignedGreaterExpr(a, b) }
                }
        }
}
