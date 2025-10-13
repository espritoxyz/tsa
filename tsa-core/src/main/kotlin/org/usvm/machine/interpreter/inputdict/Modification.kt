package org.usvm.machine.interpreter.inputdict

import org.usvm.UBoolExpr
import org.usvm.machine.TvmContext
import org.usvm.machine.splitHeadTail
import kotlin.collections.plus

/**
 * Represent the single modification over a set of dictionary keys.
 * Whenever you encounter `l: List<Modification>`, assume that `l.first()`
 * is the last modification applied (and `l.last()` is the first modification applied).
 */
sealed interface Modification {
    data class Remove(
        val key: KeyType,
    ) : Modification

    data class Store(
        val key: KeyType,
        val guard: UBoolExpr,
    ) : Modification
}

/** @return the condition that would be of a corresponding key in `getKeys` */
fun List<Modification>.createKeyCondition(
    ctx: TvmContext,
    t: KExtended,
): UBoolExpr {
    val (head, tail) = splitHeadTail() ?: return ctx.trueExpr
    val prevCond = tail.createKeyCondition(ctx, t)
    return when (head) {
        is Modification.Store -> with(ctx) { prevCond or (t eq head.key.toExtendedKey(ctx)) }
        is Modification.Remove -> with(ctx) { prevCond and (t neq head.key.toExtendedKey(ctx)) }
    }
}

fun List<Modification>.foldOnSymbols(
    ctx: TvmContext,
    base: List<GuardedKeyType>,
): List<GuardedKeyType> =
    with(ctx) {
        val (head, tail) = splitHeadTail() ?: return base
        val tailSymbols = tail.getExplicitlyStoredKeys(ctx)
        return when (head) {
            is Modification.Store ->
                tailSymbols.map { (keySymbol, cond) ->
                    GuardedKeyType(
                        keySymbol,
                        cond or (keySymbol.expr eq head.key.expr),
                    )
                } +
                    GuardedKeyType(head.key, trueExpr)

            is Modification.Remove ->
                tailSymbols.map { (symbol, condition) ->
                    GuardedKeyType(symbol, condition and (symbol.expr neq head.key.expr))
                }
        }
    }

/** @return keys that were explicitly added to the dictionary */
fun List<Modification>.getExplicitlyStoredKeys(ctx: TvmContext): List<GuardedKeyType> = foldOnSymbols(ctx, emptyList())
