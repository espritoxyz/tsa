package org.usvm.machine.interpreter

import io.ksmt.sort.KBvSort
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TvmContext
import org.usvm.machine.splitHeadTail
import org.usvm.machine.state.TvmState

typealias KeySort = TvmContext.TvmCellDataSort
typealias K = UExpr<UBvSort>
typealias KExtended = UExpr<KeySort>
typealias ConstraintSet = PersistentList<UBoolExpr>

fun TvmState.makeFreshKeyConstant(
    keySort: KBvSort,
    keyKind: TvmDictOperationInterpreter.DictKeyKind,
): KeyType = KeyType(makeSymbolicPrimitive(keySort), keyKind)

private data class ConstraintBuilder(
    private val ctx: TvmContext,
    private val constraints: MutableList<UBoolExpr> = mutableListOf(),
) {
    fun build(): PersistentList<UBoolExpr> = constraints.toPersistentList()

    fun addCs(cs: TvmContext.() -> UBoolExpr) {
        constraints += cs(ctx)
    }
}

data class KeyType(
    val expr: K,
    val kind: TvmDictOperationInterpreter.DictKeyKind,
) {
    fun toExtendedKey(ctx: TvmContext) = ctx.extendDictKey(expr, kind)
}

data class GuardedKeySymbol(
    val symbol: KeyType,
    val guard: UBoolExpr,
)

sealed interface Modification {
    data class Remove(
        val key: KeyType,
    ) : Modification

    data class Store(
        val key: KeyType,
    ) : Modification
}

sealed interface LazyUniversalQuantifierConstraint {
    val context: PersistentList<Modification>

    /**
     * @param symbol is of KExtended sort as all the comparisons must be done on the extended key type
     */
    fun createConstraint(
        ctx: TvmContext,
        symbol: KExtended,
    ): UBoolExpr
}

data class NotEqualConstraint(
    val value: KExtended,
    val condition: UBoolExpr,
    override val context: PersistentList<Modification>,
) : LazyUniversalQuantifierConstraint {
    override fun createConstraint(
        ctx: TvmContext,
        symbol: KExtended,
    ): UBoolExpr = with(ctx) { condition implies (symbol neq value) }
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

    fun createCmp(ctx: TvmContext): (KExtended, KExtended) -> UBoolExpr =
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

data class NextPrevQueryConstraint(
    val pivot: KExtended,
    val answer: KExtended,
    override val context: PersistentList<Modification>,
    val mightBeEqualToPivot: Boolean,
    val isNext: Boolean,
    val isSigned: Boolean,
) : LazyUniversalQuantifierConstraint {
    override fun createConstraint(
        ctx: TvmContext,
        symbol: KExtended,
    ): UBoolExpr {
        val pivotCmp =
            when {
                !mightBeEqualToPivot && isNext -> Cmp(CmpKind.LT)
                !mightBeEqualToPivot && !isNext -> Cmp(CmpKind.GT)
                mightBeEqualToPivot && isNext -> Cmp(CmpKind.LE)
                else -> Cmp(CmpKind.GE)
            }.copy(isSigned = isSigned).createCmp(ctx)
        val answerCmp = (if (isNext) Cmp(CmpKind.LT) else Cmp(CmpKind.GT)).createCmp(ctx)

        return with(ctx) {
            (pivotCmp(pivot, symbol) and answerCmp(symbol, answer)).not()
        }
    }
}

data class UpperLowerBoundConstraint(
    val bound: KExtended,
    val isMax: Boolean,
    val isStrictBound: Boolean,
    val isSigned: Boolean,
    override val context: PersistentList<Modification>,
) : LazyUniversalQuantifierConstraint {
    override fun createConstraint(
        ctx: TvmContext,
        symbol: KExtended,
    ): UBoolExpr {
        val cmp = Cmp(isMax, isStrictBound, isSigned)
        return cmp.createCmp(ctx)(symbol, bound)
    }
}

/** @return keys that were explicitly added to the dictionary */
fun TvmContext.getExplicitlyStoredKeys(modifications: List<Modification>): List<GuardedKeySymbol> {
    val (head, tail) = modifications.splitHeadTail() ?: return emptyList()
    val tailSymbols = getExplicitlyStoredKeys(tail)
    return when (head) {
        is Modification.Store ->
            tailSymbols.map { (keySymbol, cond) ->
                GuardedKeySymbol(
                    keySymbol,
                    cond or (keySymbol.expr eq head.key.expr),
                )
            } +
                GuardedKeySymbol(head.key, trueExpr)

        is Modification.Remove ->
            tailSymbols.map { (symbol, condition) ->
                GuardedKeySymbol(symbol, condition and (symbol.expr neq head.key.expr))
            }
    }
}

data class InputDictRootInformation(
    private val lazyUniversalQuantifierConstraints: PersistentList<LazyUniversalQuantifierConstraint> =
        persistentListOf(),
    val symbols: PersistentSet<KeyType> = persistentHashSetOf(),
) {
    /**
     * we will ensure that:
     * - (1) all the lazy constraints are applied to all the symbols
     * - (2) the lazy constraints are applied to the explicitly stored keys if necessary. One call to
     *   `addConstraint in the optimal implementation
     */
    fun addLazyUniversalConstraint(
        ctx: TvmContext,
        constraint: LazyUniversalQuantifierConstraint,
    ): Pair<ConstraintSet, PersistentList<LazyUniversalQuantifierConstraint>> {
        val constraintsBuilder = ConstraintBuilder(ctx)
        val context = constraint.context
        // ensuring (1) for already discovered symbols
        for (symbol in symbols) {
            val cs = createKeyCondition(ctx, symbol.toExtendedKey(ctx), context)
            constraintsBuilder.addCs { cs implies constraint.createConstraint(this, symbol.toExtendedKey(ctx)) }
        }
        // ensuring (2)
        val explicitlyStoredKeys = with(ctx) { getExplicitlyStoredKeys(context) }
        for ((key, cond) in explicitlyStoredKeys) {
            constraintsBuilder.addCs { cond implies constraint.createConstraint(this, key.toExtendedKey(ctx)) }
        }
        // ensuring (1) for symbols yet to be discovered
        return constraintsBuilder.build() to lazyUniversalQuantifierConstraints.add(constraint)
    }

    fun createSymbolConstraints(
        ctx: TvmContext,
        t: KExtended,
    ): ConstraintSet =
        with(ctx) {
            val constraints =
                lazyUniversalQuantifierConstraints.map { lazyConstraint ->
                    val cs = createKeyCondition(ctx, t, lazyConstraint.context)
                    cs and lazyConstraint.createConstraint(this, t)
                }
            constraints.toPersistentList()
        }

    /** @return the condition that would be of a corresponding key in `getKeys` structure */
    fun createKeyCondition(
        ctx: TvmContext,
        t: KExtended,
        modification: List<Modification>,
    ): UBoolExpr {
        val (head, tail) = modification.splitHeadTail() ?: return ctx.trueExpr
        val prevCond = createKeyCondition(ctx, t, tail)
        return when (head) {
            is Modification.Store -> with(ctx) { prevCond or (t eq head.key.toExtendedKey(ctx)) }
            is Modification.Remove -> with(ctx) { prevCond and (t neq head.key.toExtendedKey(ctx)) }
        }
    }

    fun getCurrentlyDiscoveredKeys(
        ctx: TvmContext,
        modifications: List<Modification>,
    ): List<GuardedKeySymbol> =
        with(ctx) {
            val (head, tail) = modifications.splitHeadTail() ?: return symbols.map { GuardedKeySymbol(it, trueExpr) }
            val tailSymbols = getCurrentlyDiscoveredKeys(this, tail)
            return when (head) {
                is Modification.Store ->
                    tailSymbols.map { (s, cond) ->
                        GuardedKeySymbol(
                            s,
                            cond or (s.toExtendedKey(ctx) eq head.key.toExtendedKey(ctx)),
                        )
                    } +
                        GuardedKeySymbol(head.key, trueExpr)

                is Modification.Remove ->
                    tailSymbols.map { (symbol, condition) ->
                        GuardedKeySymbol(
                            symbol,
                            condition and (symbol.toExtendedKey(ctx) neq head.key.toExtendedKey(ctx)),
                        )
                    }
            }
        }
}

sealed interface DictNextResult {
    val answer: KeyType?
        get() = null
    val constraintSet: ConstraintSet
    val newInputDictRootInformation: InputDictRootInformation

    data class Exists(
        override val answer: KeyType,
        override val constraintSet: ConstraintSet,
        override val newInputDictRootInformation: InputDictRootInformation,
    ) : DictNextResult

    data class DoesNotExist(
        override val constraintSet: ConstraintSet,
        override val newInputDictRootInformation: InputDictRootInformation,
    ) : DictNextResult
}

data class DictGetResult(
    val constraints: ConstraintSet,
    val updatedRootInfo: InputDictRootInformation,
)

sealed interface DictMaxResult {
    data class Exists(
        val constraintSet: ConstraintSet,
        val newInputDictRootInformation: InputDictRootInformation,
    ) : DictMaxResult
}

data class InputDict(
    val modifications: PersistentList<Modification> = persistentListOf(),
    val rootInputDictId: Int = next(),
) {
    companion object {
        private var counter: Int = 0

        fun next(): Int = counter++
    }

    fun doDictHasKey(
        ctx: TvmContext,
        key: KeyType,
        inputDict: InputDictRootInformation,
        freshConstantForInput: KeyType,
        freshConstantForExistenceOfKey: UBoolExpr,
    ): DictGetResult {
        val exists = freshConstantForExistenceOfKey
        val symbolConstraint = inputDict.createSymbolConstraints(ctx, freshConstantForInput.toExtendedKey(ctx))
        val resultIsSomeKey =
            freshInputSymbolOrStoredKey(
                ctx,
                freshConstantForInput.toExtendedKey(ctx),
                key.toExtendedKey(ctx),
                inputDict,
            )
        val newSymbols = inputDict.symbols.add(freshConstantForInput)
        val resultIsSomeKeyCs = with(ctx) { exists implies resultIsSomeKey }
        val (universalInstancesCs, newLazyConstraints) =
            inputDict.addLazyUniversalConstraint(
                ctx,
                NotEqualConstraint(key.toExtendedKey(ctx), ctx.mkNot(exists), modifications),
            )
        val updatedRootDictInfo =
            InputDictRootInformation(
                newLazyConstraints,
                newSymbols,
            )
        val fullCs = symbolConstraint.add(resultIsSomeKeyCs).addAll(universalInstancesCs)
        return DictGetResult(fullCs, updatedRootDictInfo)
    }

    /**
     * ```
     * max(d) = p <->
     *     p \in keys(d)
     *     \forall x <- keys(d), x <= p
     * max(d) = \bot <->
     *     \forall x <- keys(d), \bot
     * ```
     */
    fun doDictMaxMin(
        ctx: TvmContext,
        rootInformation: InputDictRootInformation,
        isMax: Boolean,
        freshConstantForInput: KeyType,
        freshConstantForResult: KeyType,
        isSigned: Boolean,
    ): DictMaxResult.Exists {
        val symbolConstraint = rootInformation.createSymbolConstraints(ctx, freshConstantForInput.toExtendedKey(ctx))
        val resultIsSomeKeyCs =
            freshInputSymbolOrStoredKey(
                ctx,
                freshConstantForInput.toExtendedKey(ctx),
                freshConstantForResult.toExtendedKey(ctx),
                rootInformation,
            )
        val newSymbols = rootInformation.symbols.add(freshConstantForInput)
        val (universalInstancesCs, newLazyConstraints) =
            rootInformation.addLazyUniversalConstraint(
                ctx,
                UpperLowerBoundConstraint(
                    freshConstantForResult.toExtendedKey(ctx),
                    isMax,
                    false,
                    isSigned,
                    modifications,
                ),
            )
        val updatedRootDictInfo = InputDictRootInformation(newLazyConstraints, newSymbols)

        val fullCs = symbolConstraint.add(resultIsSomeKeyCs).addAll(universalInstancesCs)
        return DictMaxResult.Exists(fullCs, updatedRootDictInfo)
    }

    /*
    $next(d, p) = x <->
      x > p (or equal if allowed)
      x \in keys(d) \land
      \forall k <- keys(d), k \not \in (p, x)$
     */
    fun doDictNext(
        ctx: TvmContext,
        pivot: KExtended,
        inputDict: InputDictRootInformation,
        freshConstantForInput: KeyType,
        freshConstantForResult: KeyType,
        isNext: Boolean,
        mightBeEqualToPivot: Boolean,
        isSigned: Boolean = false,
    ): List<DictNextResult> {
        val existsNextBranch =
            createExistsBranch(
                ctx,
                freshConstantForInput,
                freshConstantForResult,
                inputDict,
                mightBeEqualToPivot,
                isNext,
                isSigned,
                pivot,
            )
        val (nextCs, updatedUniversalConstraints) =
            inputDict.addLazyUniversalConstraint(
                ctx,
                UpperLowerBoundConstraint(pivot, isNext, mightBeEqualToPivot, isSigned, modifications),
            )
        val doesNotExist =
            DictNextResult.DoesNotExist(
                nextCs,
                InputDictRootInformation(updatedUniversalConstraints, inputDict.symbols),
            )

        return listOf(
            existsNextBranch,
            doesNotExist,
        )
    }

    private fun createExistsBranch(
        ctx: TvmContext,
        freshConstantForInput: KeyType,
        freshConstantForResult: KeyType,
        inputDict: InputDictRootInformation,
        mightBeEqualToPivot: Boolean,
        isNext: Boolean,
        isSigned: Boolean,
        pivot: KExtended,
    ): DictNextResult.Exists {
        val newSymbolConstraints = inputDict.createSymbolConstraints(ctx, freshConstantForInput.toExtendedKey(ctx))
        val resultIsSomeKey =
            freshInputSymbolOrStoredKey(
                ctx,
                freshConstantForInput.toExtendedKey(ctx),
                freshConstantForResult.toExtendedKey(ctx),
                inputDict,
            )
        val ansCmp = Cmp(isLess = !isNext, isStrict = !mightBeEqualToPivot, isSigned = isSigned).createCmp(ctx)
        val mainConstraint = ansCmp(freshConstantForResult.toExtendedKey(ctx), pivot)
        val (nextCs, updatedUniversalConstraints) =
            inputDict.addLazyUniversalConstraint(
                ctx,
                NextPrevQueryConstraint(
                    pivot,
                    freshConstantForResult.toExtendedKey(ctx),
                    modifications,
                    mightBeEqualToPivot,
                    isNext,
                    isSigned,
                ),
            )
        val newInputDictInfo =
            InputDictRootInformation(
                updatedUniversalConstraints,
                inputDict.symbols.add(freshConstantForInput),
            )
        val element =
            DictNextResult.Exists(
                answer = freshConstantForResult,
                constraintSet = newSymbolConstraints.add(resultIsSomeKey).add(mainConstraint).addAll(nextCs),
                newInputDictRootInformation = newInputDictInfo,
            )
        return element
    }

    /**
     * [freshConstantForInput] must be explicitly added to the list of symbols after the call
     * @return [result] to constraints
     */
    private fun freshInputSymbolOrStoredKey(
        ctx: TvmContext,
        freshConstantForInput: KExtended,
        result: KExtended,
        inputDict: InputDictRootInformation,
    ): UBoolExpr {
        val inputT: KExtended = freshConstantForInput
        val inputTCs = inputDict.createKeyCondition(ctx, inputT, modifications)
        val storedElements = with(ctx) { getExplicitlyStoredKeys(modifications) }

        val resultInKeys =
            with(ctx) {
                storedElements.fold(inputTCs and (result eq inputT)) { acc, next ->
                    acc or (next.guard and (next.symbol.toExtendedKey(ctx) eq result))
                }
            }
        return resultInKeys
    }
}
