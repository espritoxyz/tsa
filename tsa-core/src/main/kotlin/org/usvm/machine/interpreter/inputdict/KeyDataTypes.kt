package org.usvm.machine.interpreter.inputdict

import io.ksmt.sort.KBvSort
import kotlinx.collections.immutable.PersistentList
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.TvmCellDataSort
import org.usvm.machine.interpreter.TvmDictOperationInterpreter
import org.usvm.machine.state.TvmState

typealias K = UExpr<UBvSort>

/**
 * Comparisons on the keys must be done in its extended form (see comments in
 * the source of [TvmDictOperationInterpreter.doDictNextPrev]).
 * Thus, whenever we build constraints, we pass [KExtended] instances
 * to create comparison expressions.
 */
typealias KExtended = UExpr<TvmCellDataSort>

typealias ConstraintSet = PersistentList<UBoolExpr>

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

data class KeyType(
    val expr: K,
    val kind: DictKeyKind,
) {
    fun toExtendedKey(ctx: TvmContext): KExtended = ctx.extendDictKey(expr, kind)
}

data class GuardedKeyType(
    val symbol: KeyType,
    val guard: UBoolExpr,
)

fun TvmState.makeFreshKeyConstant(
    keySort: KBvSort,
    keyKind: DictKeyKind,
): KeyType = KeyType(makeSymbolicPrimitive(keySort), keyKind)
