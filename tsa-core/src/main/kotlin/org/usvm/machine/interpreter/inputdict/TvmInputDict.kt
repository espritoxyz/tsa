package org.usvm.machine.interpreter.inputdict

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.usvm.UBoolExpr
import org.usvm.machine.TvmContext

/**
 * This class represents an input dictionary (that is, a dictionary with an arbitrary content).
 * The idea of operations on the input dictionary is as follows.
 * We will maintain a list of [symbols] which will be the model of the set of keys of our dictionary
 * (in [symbols] there might be expressions that would be evaluated to the same key).
 * When we perform an operation, we would extend if needed the list of symbols and we would generate constraints
 * that guarantee the formal semantics of dictionary operation in every model of the resulted path constraints.
 *
 * Further, we will discuss the general review of the operations that semantically do not mutate the underlying dictionary.
 * We will call such operations *queries*.
 * For the descriptions of semantically mutating operations, see the docs in [InputDict].
 *
 * To perform a query, we write down the mathematical formula that describes the semantics of a query.
 * Such a formula will usually contain quantifiers over `dict.keys`, either universal or existential.
 * To satisfy the existential quantifier over the set of `dict.keys`, we will generate a fresh symbol, add it to
 * [symbols] and use it as a witness for the quantifier. To satisfy the universal quantifier constraint,
 * we will store this constraint in [lazyUniversalQuantifierConstraints] and *lazily* instantiate it for all symbols in
 * [symbols]. In particular, we would add an instance of the constraint for every `symbol` that would be added to [symbols]
 * later in the execution.
 *
 * For example, `getNext` must a return a key in the dictionary which is also the minimal key not greater than `p`
 * (see [func docs](https://docs.ton.org/v3/documentation/smart-contracts/func/docs/stdlib/#dict_get_next)).
 * We would write the semantics of this query as follows:
 * ```
 * getNext(p) = a <->
 *     \exists k <- dict.keys: k = a \land
 *     \forall k <- dict.keys, k > p \implies k >= a
 * ```
 * To answer this query, we would create a fresh symbol that would be the answer to our query (the `a`)
 * and add a [NextPrevQueryConstraint] via [addLazyUniversalConstraint] that is responsible for instantiating quantifiers.
 *
 */
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
    ): Pair<PersistentList<UBoolExpr>, PersistentList<LazyUniversalQuantifierConstraint>> {
        val constraintsBuilder = ConstraintBuilder(ctx)
        // ensuring (1) for already discovered symbols
        for (symbol in symbols) {
            val cs = constraint.modifications.createKeyCondition(ctx, symbol.toExtendedKey(ctx))
            constraintsBuilder.addCs { cs implies constraint.createConstraint(this, symbol.toExtendedKey(ctx)) }
        }
        // ensuring (2)
        val explicitlyStoredKeys = constraint.modifications.getExplicitlyStoredKeys(ctx)
        for ((key, cond) in explicitlyStoredKeys) {
            constraintsBuilder.addCs { cond implies constraint.createConstraint(this, key.toExtendedKey(ctx)) }
        }
        // ensuring (1) for symbols yet to be discovered
        return constraintsBuilder.build() to lazyUniversalQuantifierConstraints.add(constraint)
    }

    fun createSymbolConstraints(
        ctx: TvmContext,
        t: KExtended,
    ): PersistentList<UBoolExpr> =
        with(ctx) {
            val constraints =
                lazyUniversalQuantifierConstraints.map { lazyConstraint ->
                    val cs = lazyConstraint.modifications.createKeyCondition(ctx, t)
                    cs and lazyConstraint.createConstraint(this, t)
                }
            constraints.toPersistentList()
        }
}

sealed interface DictNextResult {
    val answer: KeyType?
        get() = null
    val constraintSet: PersistentList<UBoolExpr>
    val newInputDictRootInformation: InputDictRootInformation

    data class Exists(
        override val answer: KeyType,
        override val constraintSet: PersistentList<UBoolExpr>,
        override val newInputDictRootInformation: InputDictRootInformation,
    ) : DictNextResult

    data class DoesNotExist(
        override val constraintSet: PersistentList<UBoolExpr>,
        override val newInputDictRootInformation: InputDictRootInformation,
    ) : DictNextResult
}

data class DictGetResult(
    val constraints: PersistentList<UBoolExpr>,
    val updatedRootInfo: InputDictRootInformation,
)

sealed interface DictMaxResult {
    data class Exists(
        val constraintSet: PersistentList<UBoolExpr>,
        val newInputDictRootInformation: InputDictRootInformation,
    ) : DictMaxResult
}

/**
 * To perform mutating operation on an input dictionary, we represent each
 * input dictionary as some root input dictionary and a list of [modifications]
 * performed on top of it (`modifications.first()` is the last operation applied).
 * The general formalism for queries to the input dictionary is the same as described in
 * [InputDictRootInformation], with the only difference being the key calculation.
 * To get the keys of the dictionary, one would take the keys of the root dictionary
 * and create a condition expression that would represent whether this element belongs to `this`
 * (see [getCurrentlyDiscoveredKeys] and [createKeyCondition] implementations).
 *
 * The root information is referenced via [rootInputDictId]; for explanations, see
 * [InputDictionaryStorage] docs.
 */
data class InputDict(
    val modifications: PersistentList<Modification> = persistentListOf(),
    val rootInputDictId: Int = nextId(),
) {
    fun withModification(modification: Modification): InputDict =
        InputDict(modifications.add(0, modification), rootInputDictId)

    companion object {
        private var counter: Int = 0

        fun nextId(): Int = counter++
    }

    internal fun getCurrentlyDiscoveredKeys(
        ctx: TvmContext,
        rootInformation: InputDictRootInformation,
    ): List<GuardedKeyType> =
        modifications.foldOnSymbols(ctx, rootInformation.symbols.map { GuardedKeyType(it, ctx.trueExpr) })

    /**
     * most probably, you want to use [doInputDictHasKey] instead as a less error-prone version
     */
    fun doDictHasKeyImpl(
        ctx: TvmContext,
        key: KeyType,
        rootInfo: InputDictRootInformation,
        freshConstantForInput: KeyType,
        freshConstantForExistenceOfKey: UBoolExpr,
    ): DictGetResult {
        val exists = freshConstantForExistenceOfKey
        val symbolConstraint = rootInfo.createSymbolConstraints(ctx, freshConstantForInput.toExtendedKey(ctx))
        val rootInfoWithNewSymbol = rootInfo.copy(symbols = rootInfo.symbols.add(freshConstantForInput))
        val resultIsSomeKey =
            freshInputSymbolOrStoredKey(
                ctx,
                freshConstantForInput.toExtendedKey(ctx),
                key.toExtendedKey(ctx),
            )
        val resultIsSomeKeyCs = with(ctx) { exists implies resultIsSomeKey }
        val (universalInstancesCs, newLazyConstraints) =
            rootInfoWithNewSymbol.addLazyUniversalConstraint(
                ctx,
                NotEqualConstraint(key.toExtendedKey(ctx), ctx.mkNot(exists), modifications),
            )
        val updatedRootDictInfo =
            InputDictRootInformation(
                newLazyConstraints,
                rootInfoWithNewSymbol.symbols,
            )
        val fullCs = symbolConstraint.add(resultIsSomeKeyCs).addAll(universalInstancesCs)
        return DictGetResult(fullCs, updatedRootDictInfo)
    }

    /**
     * ```
     * max(d) = p <->
     *     p \in keys(d)
     *     \forall x <- keys(d), x <= p
     * ```
     */
    fun doDictMaxMinImpl(
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

    /**
     *  ```    
     *  next(d, p) = x <->
     *      x > p (or equal if allowed)
     *      x \in keys(d)
     *      \forall k <- keys(d), k \not \in (p, x)$
     * ```
     */
    fun doDictNextImpl(
        ctx: TvmContext,
        pivot: KExtended,
        inputDict: InputDictRootInformation,
        freshConstantForInput: KeyType,
        freshConstantForResult: KeyType,
        isNext: Boolean,
        mightBeEqualToPivot: Boolean,
        isSigned: Boolean,
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
    ): UBoolExpr {
        val inputT: KExtended = freshConstantForInput
        val inputTCs = modifications.createKeyCondition(ctx, inputT)
        val storedElements = modifications.getExplicitlyStoredKeys(ctx)

        val resultInKeys =
            with(ctx) {
                storedElements.fold(inputTCs and (result eq inputT)) { acc, next ->
                    acc or (next.guard and (next.symbol.toExtendedKey(ctx) eq result))
                }
            }
        val someConstraintHeld =
            with(ctx) {
                storedElements.fold(inputTCs) { acc, next ->
                    acc or next.guard
                }
            }
        return ctx.mkAnd(resultInKeys, someConstraintHeld)
    }
}
