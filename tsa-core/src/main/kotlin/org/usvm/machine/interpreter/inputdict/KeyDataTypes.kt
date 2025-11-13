package org.usvm.machine.interpreter.inputdict

import io.ksmt.sort.KBvSort
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.TvmCellDataSort
import org.usvm.machine.interpreter.TvmDictOperationInterpreter
import org.usvm.machine.state.TvmState

/**
 * Comparisons on the keys must be done in its extended form (see comments in
 * the source of [TvmDictOperationInterpreter.doDictNextPrev]).
 * Thus, whenever we build constraints, we pass [ExtendedDictKey] instances
 * to create comparison expressions.
 */
typealias ExtendedDictKey = UExpr<TvmCellDataSort>

enum class DictKeyKind {
    SIGNED_INT,
    UNSIGNED_INT,
    SLICE,
}

fun TvmContext.extendDictKey(
    value: UExpr<UBvSort>,
    keyType: DictKeyKind,
): UExpr<TvmCellDataSort> =
    when (keyType) {
        DictKeyKind.SIGNED_INT -> value.signExtendToSort(cellDataSort)

        DictKeyKind.UNSIGNED_INT,
        DictKeyKind.SLICE,
        -> value.zeroExtendToSort(cellDataSort)
    }

data class TypedDictKey(
    val expr: UExpr<UBvSort>,
    val kind: DictKeyKind,
) {
    fun toExtendedKey(ctx: TvmContext): ExtendedDictKey = ctx.extendDictKey(expr, kind)
}

data class GuardedTypedDictKey(
    val symbol: TypedDictKey,
    val guard: UBoolExpr,
)

fun TvmState.makeFreshKeyConstant(
    keySort: KBvSort,
    keyKind: DictKeyKind,
): TypedDictKey = TypedDictKey(makeSymbolicPrimitive(keySort), keyKind)
