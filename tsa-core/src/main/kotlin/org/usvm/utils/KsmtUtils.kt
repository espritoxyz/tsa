package org.usvm.utils

import io.ksmt.expr.KBitVecValue
import io.ksmt.expr.KBvConcatExpr
import io.ksmt.expr.KBvZeroExtensionExpr
import io.ksmt.expr.KEqExpr
import io.ksmt.expr.KInterpretedValue
import io.ksmt.expr.KIteExpr
import io.ksmt.expr.KNotExpr
import io.ksmt.sort.KBvSort
import io.ksmt.utils.uncheckedCast
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
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.bigIntValue
import org.usvm.machine.types.mkIte
import org.usvm.memory.foldHeapRef
import kotlin.math.min

private const val MAX_RECURSION_DEPTH = 100

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

fun <Sort : USort> UExpr<Sort>.tryTransformToIteWithConcreteLeaves(depth: Int = 0): UExpr<Sort>? {
    if (depth > MAX_RECURSION_DEPTH) return null
    return with(ctx.tctx()) {
        when (this@tryTransformToIteWithConcreteLeaves) {
            is KInterpretedValue -> {
                this@tryTransformToIteWithConcreteLeaves
            }

            is KBvConcatExpr -> {
                val newArg0 =
                    arg0.tryTransformToIteWithConcreteLeaves(depth + 1)
                        ?: return null
                val newArg1 =
                    arg1.tryTransformToIteWithConcreteLeaves(depth + 1)
                        ?: return null
                if (newArg0 is KIteExpr && newArg1 is KInterpretedValue) {
                    return mkIte(
                        newArg0.condition,
                        mkBvConcatExpr(newArg0.trueBranch, newArg1),
                        mkBvConcatExpr(newArg0.falseBranch, newArg1),
                    ).uncheckedCast()
                }
                if (newArg1 is KIteExpr && newArg0 is KInterpretedValue) {
                    return mkIte(
                        newArg1.condition,
                        mkBvConcatExpr(newArg0, newArg1.trueBranch),
                        mkBvConcatExpr(newArg0, newArg1.falseBranch),
                    ).uncheckedCast()
                }
                if (newArg0 is KIteExpr && newArg1 is KIteExpr && newArg0.condition == newArg1.condition) {
                    return mkIte(
                        newArg0.condition,
                        mkBvConcatExpr(newArg0.trueBranch, newArg1.trueBranch),
                        mkBvConcatExpr(newArg0.falseBranch, newArg1.falseBranch),
                    ).uncheckedCast()
                }
                null
            }

            is KIteExpr -> {
                val newTrueBranch =
                    trueBranch.tryTransformToIteWithConcreteLeaves(depth + 1)
                        ?: return null
                val newFalseBranch =
                    falseBranch.tryTransformToIteWithConcreteLeaves(depth + 1)
                        ?: return null

                mkIte(condition, newTrueBranch, newFalseBranch)
            }

            is KBvZeroExtensionExpr -> {
                val asConcat = mkBvConcatExpr(mkBv(0, extensionSize.toUInt()), value)
                asConcat.tryTransformToIteWithConcreteLeaves(depth + 1).uncheckedCast()
            }

            else -> {
                null
            }
        }
    }
}

fun <Sort : USort> UExpr<Sort>.isIteWithConcreteLeaves(depth: Int = 0): Boolean {
    if (depth > MAX_RECURSION_DEPTH) return false
    if (this is KInterpretedValue) {
        return true
    }
    if (this !is KIteExpr) {
        return false
    }
    return trueBranch.isIteWithConcreteLeaves(depth + 1) && falseBranch.isIteWithConcreteLeaves(depth + 1)
}

fun unpackConcat(
    expr: UExpr<KBvSort>,
    depth: Int = 0,
): List<UExpr<UBvSort>>? {
    if (depth > MAX_RECURSION_DEPTH) {
        return null
    }
    return with(expr.ctx) {
        if (expr.isIteWithConcreteLeaves()) {
            val bits = expr.sort.sizeBits.toInt()
            return List(bits) { i ->
                mkBvExtractExpr(high = bits - i - 1, low = bits - i - 1, expr)
            }
        }
        when (expr) {
            is KBvZeroExtensionExpr -> {
                val unpacked = unpackConcat(expr.value, depth + 1) ?: return null
                List(expr.extensionSize) { mkBv(0, 1u) } + unpacked
            }

            is KBvConcatExpr -> {
                val unpackedArg0 = unpackConcat(expr.arg0, depth + 1) ?: return null
                val unpackedArg1 = unpackConcat(expr.arg1, depth + 1) ?: return null
                unpackedArg0 + unpackedArg1
            }

            else -> {
                listOf(expr)
            }
        }
    }
}

fun groupIntoParts(
    a: UExpr<KBvSort>,
    b: UExpr<KBvSort>,
): List<Pair<UExpr<KBvSort>, UExpr<KBvSort>>>? {
    check(a.sort == b.sort) {
        "Incompatible sorts"
    }

    if (a is KInterpretedValue && b is KInterpretedValue) {
        return listOf(a to b)
    }

    val aParts = ArrayDeque(unpackConcat(a) ?: return null)
    val bParts = ArrayDeque(unpackConcat(b) ?: return null)

    val result = mutableListOf<Pair<UExpr<KBvSort>, UExpr<KBvSort>>>()

    lateinit var curA: UExpr<KBvSort>
    var endA = 0
    lateinit var curB: UExpr<KBvSort>
    var endB = 0
    var ptr = 0

    fun isCompatibleForMerge(
        a: UExpr<KBvSort>,
        b: UExpr<KBvSort>,
    ): Boolean {
        if (a is KInterpretedValue && b is KInterpretedValue) {
            return true
        }
        if (a is KIteExpr && b is KIteExpr && a.condition == b.condition) {
            return true
        }
        return false
    }

    while (true) {
        if (ptr == endA && endA == endB) {
            if (ptr > 0) {
                check(curA.sort == curB.sort) {
                    "Incompatible sorts"
                }
                if (result.isNotEmpty() &&
                    isCompatibleForMerge(result.last().first, curA) &&
                    isCompatibleForMerge(result.last().second, curB)
                ) {
                    val last = result.removeLast()
                    with(a.ctx) {
                        result.add(mkBvConcatExpr(last.first, curA) to mkBvConcatExpr(last.second, curB))
                    }
                } else {
                    result.add(curA to curB)
                }
            }
            if (aParts.isEmpty() && bParts.isEmpty()) {
                return result
            }
            if (aParts.isEmpty() || bParts.isEmpty()) {
                return null
            }
            curA = aParts.removeFirst()
            endA += curA.sort.sizeBits.toInt()
            curB = bParts.removeFirst()
            endB += curB.sort.sizeBits.toInt()
        }

        with(a.ctx) {
            if (endA == ptr) {
                if (aParts.isEmpty()) {
                    return null
                }
                val part = aParts.removeFirst()
                curA = mkBvConcatExpr(curA, part)
                endA += part.sort.sizeBits.toInt()
                if (endA > endB) {
                    return null
                }
            }

            if (endB == ptr) {
                if (bParts.isEmpty()) {
                    return null
                }
                val part = bParts.removeFirst()
                curB = mkBvConcatExpr(curB, part)
                endB += part.sort.sizeBits.toInt()
                if (endB > endA) {
                    return null
                }
            }
        }

        ptr = min(endA, endB)
    }
}
