package org.usvm.machine.interpreter

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.machine.TvmContext
import org.usvm.machine.splitHeadTail

typealias KeySort = TvmContext.TvmCellDataSort
typealias K = UExpr<KeySort>
typealias ConstraintSet = PersistentList<UBoolExpr>

private data class ConstraintBuilder(
    private val ctx: TvmContext,
    private val constraints: MutableList<UBoolExpr> = mutableListOf(),
) {
    fun build(): PersistentList<UBoolExpr> = constraints.toPersistentList()

    fun addCs(cs: TvmContext.() -> UBoolExpr) {
        constraints += cs(ctx)
    }
}

data class KeySymbol(
    val symbol: K,
    val guard: UBoolExpr,
)

sealed interface Modification {
    data class Remove(
        val k: K,
    ) : Modification

    data class Store(
        val k: K,
    ) : Modification
}

sealed interface LazyUniversalQuantifierConstraint {
    /** I */
    val context: PersistentList<Modification>

    fun createConstraint(
        ctx: TvmContext,
        symbol: K,
    ): UBoolExpr
}

enum class CmpKind {
    LE,
    LT,
    GE,
    GT,
}

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

    fun createCmp(ctx: TvmContext): (K, K) -> UBoolExpr =
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
    val pivot: K,
    val answer: K,
    override val context: PersistentList<Modification>,
    val mightBeEqualToPivot: Boolean,
    val isNext: Boolean,
    val isSigned: Boolean,
) : LazyUniversalQuantifierConstraint {
    override fun createConstraint(
        ctx: TvmContext,
        symbol: K,
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
    val bound: K,
    val isMax: Boolean,
    val isStrictBound: Boolean,
    val isSigned: Boolean,
    override val context: PersistentList<Modification>,
) : LazyUniversalQuantifierConstraint {
    override fun createConstraint(
        ctx: TvmContext,
        symbol: K,
    ): UBoolExpr {
        val cmp = Cmp(isMax, isStrictBound, isSigned)
        return cmp.createCmp(ctx)(symbol, bound)
    }
}

/** @return keys that were explicitly added to the dictionary */
fun TvmContext.getExplicitlyStoredKeys(modifications: List<Modification>): List<KeySymbol> {
    val (head, tail) = modifications.splitHeadTail() ?: return emptyList()
    val tailSymbols = getExplicitlyStoredKeys(tail)
    return when (head) {
        is Modification.Store ->
            tailSymbols.map { (s, cond) -> KeySymbol(s, cond or (s eq head.k)) } +
                KeySymbol(head.k, trueExpr)

        is Modification.Remove ->
            tailSymbols.map { (symbol, condition) ->
                KeySymbol(symbol, condition and (symbol neq head.k))
            }
    }
}

data class InputDictRootInformation(
    private val lazyUniversalQuantifierConstraints: PersistentList<LazyUniversalQuantifierConstraint> =
        persistentListOf(),
    val symbols: PersistentSet<K> = persistentHashSetOf(),
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
            val cs = createKeyCondition(ctx, symbol, context)
            constraintsBuilder.addCs { cs implies constraint.createConstraint(this, symbol) }
        }
        // ensuring (2)
        val explicitlyStoredKeys = with(ctx) { getExplicitlyStoredKeys(context) }
        for ((s, cond) in explicitlyStoredKeys) {
            constraintsBuilder.addCs { cond implies constraint.createConstraint(this, s) }
        }
        // ensuring (1) for symbols yet to be discovered
        return constraintsBuilder.build() to lazyUniversalQuantifierConstraints.add(constraint)
    }

    fun createSymbolConstraints(
        ctx: TvmContext,
        t: K,
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
        t: K,
        modification: List<Modification>,
    ): UBoolExpr {
        val (head, tail) = modification.splitHeadTail() ?: return ctx.trueExpr
        val prevCond = createKeyCondition(ctx, t, tail)
        return when (head) {
            is Modification.Store -> with(ctx) { prevCond or (t eq head.k) }
            is Modification.Remove -> with(ctx) { prevCond and (t neq head.k) }
        }
    }

    fun TvmContext.getCurrentlyDiscoveredKeys(modifications: List<Modification>): List<KeySymbol> {
        val (head, tail) = modifications.splitHeadTail() ?: return symbols.map { KeySymbol(it, trueExpr) }
        val tailSymbols = getCurrentlyDiscoveredKeys(tail)
        return when (head) {
            is Modification.Store ->
                tailSymbols.map { (s, cond) -> KeySymbol(s, cond or (s eq head.k)) } +
                    KeySymbol(head.k, trueExpr)

            is Modification.Remove ->
                tailSymbols.map { (symbol, condition) ->
                    KeySymbol(symbol, condition and (symbol neq head.k))
                }
        }
    }
}

sealed interface DictNextResult {
    val answer: K?
        get() = null
    val constraintSet: ConstraintSet
    val newInputDictRootInformation: InputDictRootInformation

    data class Exists(
        override val answer: K,
        override val constraintSet: ConstraintSet,
        override val newInputDictRootInformation: InputDictRootInformation,
    ) : DictNextResult

    data class DoesNotExist(
        override val constraintSet: ConstraintSet,
        override val newInputDictRootInformation: InputDictRootInformation,
    ) : DictNextResult
}

data class InputDict(
    val rootInputDictId: Int = next(),
    val modifications: PersistentList<Modification> = persistentListOf(),
) {
    companion object {
        private var counter: Int = 0

        fun next(): Int = counter++
    }

    /*
    $next(d, p) = x <->
      x > p (or equal if allowed)
      x \in keys(d) \land
      \forall k <- keys(d), k \not \in (p, x)$
     */
    fun doDictNext(
        ctx: TvmContext,
        pivot: K,
        inputDict: InputDictRootInformation,
        freshConstantForInput: K,
        freshConstantForResult: K,
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
        freshConstantForInput: K,
        freshConstantForResult: K,
        inputDict: InputDictRootInformation,
        mightBeEqualToPivot: Boolean,
        isNext: Boolean,
        isSigned: Boolean,
        pivot: K,
    ): DictNextResult.Exists {
        val (t, cs) = freshInputSymbolOrStoredKey(ctx, freshConstantForInput, freshConstantForResult, inputDict)
        val ansCmp = Cmp(isLess = !isNext, isStrict = !mightBeEqualToPivot, isSigned = isSigned).createCmp(ctx)
        val mainConstraint = ansCmp(t, pivot)
        val (nextCs, updatedUniversalConstraints) =
            inputDict.addLazyUniversalConstraint(
                ctx,
                NextPrevQueryConstraint(pivot, t, modifications, mightBeEqualToPivot, isNext, isSigned),
            )
        val newInputDictInfo =
            InputDictRootInformation(
                updatedUniversalConstraints,
                inputDict.symbols.add(freshConstantForInput),
            )
        val element =
            DictNextResult.Exists(
                answer = t,
                constraintSet = cs.add(mainConstraint).addAll(nextCs),
                newInputDictRootInformation = newInputDictInfo,
            )
        return element
    }

    /**
     * [freshConstantForInput] must be explicitly added to the list of symbols
     */
    private fun freshInputSymbolOrStoredKey(
        ctx: TvmContext,
        freshConstantForInput: K,
        freshConstantForResult: K,
        inputDict: InputDictRootInformation,
    ): Pair<K, ConstraintSet> {
        val inputT: K = freshConstantForInput
        val newSymbolConstraints = inputDict.createSymbolConstraints(ctx, inputT)
        val inputTCs = inputDict.createKeyCondition(ctx, inputT, modifications)
        val storedElements = with(ctx) { getExplicitlyStoredKeys(modifications) }
        val t = freshConstantForResult // we will return this symbol
        val tIsInKeys =
            with(ctx) {
                storedElements.fold(inputTCs and (t eq inputT)) { acc, next ->
                    acc or (next.guard and (next.symbol eq t))
                }
            }
        val fullCs = newSymbolConstraints.add(tIsInKeys)
        return t to fullCs
    }
}
