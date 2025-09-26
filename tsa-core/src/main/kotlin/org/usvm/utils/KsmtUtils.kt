package org.usvm.utils

import io.ksmt.expr.KBitVecValue
import io.ksmt.expr.KEqExpr
import io.ksmt.expr.KNotExpr
import io.ksmt.sort.KBvSort
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.USort
import org.usvm.isAllocated
import org.usvm.isStatic
import org.usvm.machine.TvmContext
import org.usvm.machine.bigIntValue
import org.usvm.memory.foldHeapRef

val UExpr<out KBvSort>.intValueOrNull: Int?
    get() = (this as? KBitVecValue<*>)?.bigIntValue()?.toInt()

fun TvmContext.flattenReferenceIte(
    ref: UHeapRef,
    extractAllocated: Boolean = false,
    extractStatic: Boolean = true,
): List<Pair<UBoolExpr, UConcreteHeapRef>> =
    foldHeapRef(
        ref,
        initial = emptyList(),
        initialGuard = trueExpr,
        staticIsConcrete = true,
        blockOnSymbolic = { _, (ref, _) -> error("Unexpected ref $ref") },
        blockOnConcrete = { acc, (expr, guard) ->
            if (expr.isStatic && extractStatic || expr.isAllocated && extractAllocated) {
                acc + (guard to expr)
            } else {
                acc
            }
        },
    )

/**
 * In a case of concrete dict, after reading with a symbolic key,
 * our [ref] is a big ITE with concrete values in its conditions.
 *
 * For example:
 * ```
 * ite(
 *     key eq concreteKey1,
 *     trueBranch = concreteValue1,
 *     falseBranch = ite(
 *         key eq concreteKey2,
 *         trueBranch = concreteValue2,
 *         falseBranch = ite(
 *             key eq concreteKey3,
 *             trueBranch = concreteValue3,
 *             falseBranch = concreteValue4
 *         )
 *     )
 * )
 * ```
 *
 * With standard [flattenReferenceIte], we get such guards:
 * - key eq concreteKey1
 * - (key eq concreteKey2) and (key neq concreteKey1)
 * - (key eq concreteKey3) and (key neq concreteKey1) and (key neq concreteKey2)
 *
 * By using this function, we get shorter guards in this specific case:
 * - key eq concreteKey1
 * - key eq concreteKey2
 * - key eq concreteKey3
 * */
fun TvmContext.flattenReferenceIteSpecialized(
    ref: UHeapRef,
    extractAllocated: Boolean = true,
    extractStatic: Boolean = true,
): List<Pair<UBoolExpr, UConcreteHeapRef>> {
    val queue = mutableListOf(Guard(persistentMapOf(), trueExpr) to ref)
    val result = mutableListOf<Pair<UBoolExpr, UConcreteHeapRef>>()
    while (queue.isNotEmpty()) {
        val (guard, cur) = queue.removeFirst()
        if (cur is UIteExpr<UAddressSort>) {
            val curGuard = Guard.fromUBoolExpr(cur.condition)
            queue.add(guard.mkAnd(curGuard) to cur.trueBranch)
            queue.add(guard.mkAnd(curGuard.mkNot()) to cur.falseBranch)
        } else if (cur is UConcreteHeapRef && cur.isAllocated) {
            if (extractAllocated) {
                result.add(guard.toUBoolExpr() to cur)
            }
        } else if (cur is UConcreteHeapRef && cur.isStatic) {
            if (extractStatic) {
                result.add(guard.toUBoolExpr() to cur)
            }
        } else {
            error("Unexpected ref: $cur")
        }
    }

    return result
}

private data class Guard(
    val symbolToConcreteValues: PersistentMap<UExpr<UBvSort>, SymbolValues>,
    val otherGuard: UBoolExpr,
) {
    fun toUBoolExpr(): UBoolExpr =
        with(otherGuard.ctx) {
            var result = otherGuard
            symbolToConcreteValues.forEach { (symbol, values) ->
                val cur =
                    when (values.specificValues.size) {
                        0 -> {
                            values.forbiddenValues.fold(trueExpr as UBoolExpr) { acc, elem ->
                                mkAnd(acc, (symbol neq elem), flat = false)
                            }
                        }
                        1 -> {
                            val value = values.specificValues.single()
                            if (value in values.forbiddenValues) {
                                falseExpr
                            } else {
                                symbol eq value
                            }
                        }
                        else -> {
                            falseExpr
                        }
                    }
                result = result and cur
            }
            result
        }

    fun mkNot(): Guard =
        with(otherGuard.ctx) {
            val boolExpr = toUBoolExpr()
            return fromUBoolExpr(boolExpr.not())
        }

    fun mkAnd(other: Guard): Guard =
        with(otherGuard.ctx) {
            val commonKeys = other.symbolToConcreteValues.keys intersect symbolToConcreteValues.keys

            val disjointMap =
                symbolToConcreteValues.minus(other.symbolToConcreteValues.keys).putAll(
                    other.symbolToConcreteValues.minus(symbolToConcreteValues.keys),
                )

            val intersection =
                commonKeys.associateWith {
                    SymbolValues(
                        specificValues =
                            symbolToConcreteValues[it]!!.specificValues.addAll(
                                other.symbolToConcreteValues[it]!!.specificValues,
                            ),
                        forbiddenValues =
                            symbolToConcreteValues[it]!!.forbiddenValues.addAll(
                                other.symbolToConcreteValues[it]!!.forbiddenValues,
                            ),
                    )
                }

            val values = disjointMap.putAll(intersection)

            return Guard(
                symbolToConcreteValues = values,
                otherGuard = otherGuard and other.otherGuard,
            )
        }

    companion object {
        private fun specificValue(
            expr: UExpr<UBvSort>,
            value: KBitVecValue<UBvSort>,
        ): Guard =
            with(expr.ctx) {
                Guard(
                    symbolToConcreteValues =
                        persistentMapOf(
                            expr to
                                SymbolValues(
                                    forbiddenValues = persistentSetOf(),
                                    specificValues = persistentSetOf(value),
                                ),
                        ),
                    otherGuard = trueExpr,
                )
            }

        private fun forbiddenValue(
            expr: UExpr<UBvSort>,
            value: KBitVecValue<UBvSort>,
        ): Guard =
            with(expr.ctx) {
                Guard(
                    symbolToConcreteValues =
                        persistentMapOf(
                            expr to
                                SymbolValues(
                                    forbiddenValues = persistentSetOf(value),
                                    specificValues = persistentSetOf(),
                                ),
                        ),
                    otherGuard = trueExpr,
                )
            }

        fun fromUBoolExpr(guard: UBoolExpr): Guard {
            if (guard is KEqExpr<*> && guard.lhs is KBitVecValue) {
                @Suppress("unchecked_cast")
                return specificValue(guard.rhs as UExpr<UBvSort>, guard.lhs as KBitVecValue<UBvSort>)
            }
            if (guard is KEqExpr<*> && guard.rhs is KBitVecValue) {
                @Suppress("unchecked_cast")
                return specificValue(guard.lhs as UExpr<UBvSort>, guard.rhs as KBitVecValue<UBvSort>)
            }
            if (guard is KNotExpr && (guard.arg as? KEqExpr<*>)?.lhs is KBitVecValue) {
                @Suppress("unchecked_cast")
                val arg = guard.arg as KEqExpr<UBvSort>
                return forbiddenValue(arg.rhs, arg.lhs as KBitVecValue<UBvSort>)
            }
            if (guard is KNotExpr && (guard.arg as? KEqExpr<*>)?.rhs is KBitVecValue) {
                @Suppress("unchecked_cast")
                val arg = guard.arg as KEqExpr<UBvSort>
                return forbiddenValue(arg.lhs, arg.rhs as KBitVecValue<UBvSort>)
            }
            return Guard(persistentMapOf(), otherGuard = guard)
        }
    }
}

private data class SymbolValues(
    val forbiddenValues: PersistentSet<KBitVecValue<UBvSort>>,
    val specificValues: PersistentSet<KBitVecValue<UBvSort>>,
)

fun <Sort : USort> TvmContext.splitAndRead(
    ref: UHeapRef,
    read: (UConcreteHeapRef) -> UExpr<Sort>,
): UExpr<Sort> {
    val refs = flattenReferenceIte(ref, extractAllocated = true, extractStatic = true)
    val values =
        refs.map { (guard, concreteRef) ->
            guard to read(concreteRef)
        }

    return values.drop(1).fold(values.first().second) { acc, (guard, value) ->
        mkIte(
            guard,
            trueBranch = value,
            falseBranch = acc,
        )
    }
}
