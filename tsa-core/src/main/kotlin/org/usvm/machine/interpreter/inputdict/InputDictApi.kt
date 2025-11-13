package org.usvm.machine.interpreter.inputdict

import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TvmStepScopeManager

data class DictHasKeyResult(
    val exists: UBoolExpr,
)

/**
 * @return `null` if the scope is dead
 */
fun doInputDictHasKey(
    scope: TvmStepScopeManager,
    inputDict: InputDict,
    key: TypedDictKey,
) = with(scope.ctx) {
    val ctx = scope.ctx
    val keyExists = scope.calcOnState { makeSymbolicPrimitive(boolSort) }
    val freshKeyConst = scope.calcOnState { makeFreshKeyConstant(key.expr.sort, key.kind) }
    val rootInputDictionary =
        scope.calcOnState { inputDictionaryStorage.getRootInfoByIdOrThrow(inputDict.rootInputDictId) }
    val result = inputDict.doDictHasKeyImpl(ctx, key, rootInputDictionary, freshKeyConst, keyExists)
    scope.assert(mkAnd(result.constraints)) ?: return@with null
    scope.calcOnState {
        inputDictionaryStorage =
            inputDictionaryStorage.updateRootInputDictionary(inputDict.rootInputDictId, result.updatedRootInfo)
    }
    DictHasKeyResult(keyExists)
}

fun assertInputDictIsEmpty(
    scope: TvmStepScopeManager,
    inputDict: InputDict,
    guard: UBoolExpr,
): Unit? {
    val ctx = scope.ctx
    val rootInputDictionary =
        scope.calcOnState { inputDictionaryStorage.getRootInfoByIdOrThrow(inputDict.rootInputDictId) }
    val (cs, quantifiers) =
        rootInputDictionary.addLazyUniversalConstraint(
            ctx,
            EmptyDictConstraint(guard, inputDict.modifications),
        )
    scope.assert(ctx.mkAnd(cs))
        ?: return null
    scope.calcOnState {
        inputDictionaryStorage =
            inputDictionaryStorage.updateRootInputDictionary(
                inputDict.rootInputDictId,
                InputDictRootInformation(quantifiers, rootInputDictionary.symbols),
            )
    }
    return Unit
}

data class DictMinMaxResult(
    val expr: TypedDictKey,
)

fun doInputDictMinMax(
    scope: TvmStepScopeManager,
    inputDict: InputDict,
    isMax: Boolean,
    dictKeySort: UBvSort,
    dictKeyKind: DictKeyKind,
): DictMinMaxResult? {
    val ctx = scope.ctx
    val keyResultSymbol = scope.calcOnState { makeFreshKeyConstant(dictKeySort, dictKeyKind) }
    val freshConstantForInput = scope.calcOnState { makeFreshKeyConstant(dictKeySort, dictKeyKind) }
    val rootInformation =
        scope.calcOnState { inputDictionaryStorage.rootInformation[inputDict.rootInputDictId] }
            ?: error("no registry for the root id ${inputDict.rootInputDictId}")
    val dictMaxResult =
        inputDict.doDictMaxMinImpl(
            ctx,
            rootInformation,
            isMax,
            freshConstantForInput,
            keyResultSymbol,
            dictKeyKind == DictKeyKind.SIGNED_INT,
        )
    scope.assert(ctx.mkAnd(dictMaxResult.constraintSet))
        ?: return null

    scope.calcOnState {
        inputDictionaryStorage =
            inputDictionaryStorage.updateRootInputDictionary(
                inputDict.rootInputDictId,
                dictMaxResult.newInputDictRootInformation,
            )
    }
    return DictMinMaxResult(keyResultSymbol)
}

fun doInputDictNextPrev(
    scope: TvmStepScopeManager,
    inputDict: InputDict,
    keyExtended: ExtendedDictKey,
    dictKeySort: UBvSort,
    dictKeyKind: DictKeyKind,
    isNext: Boolean,
    mightBeEqualToPivot: Boolean,
    doWithResult: TvmStepScopeManager.(DictNextResult) -> Unit,
) {
    val ctx = scope.ctx
    val resultSymbol = scope.calcOnState { makeFreshKeyConstant(dictKeySort, dictKeyKind) }
    val freshConstantForInput = scope.calcOnState { makeFreshKeyConstant(dictKeySort, dictKeyKind) }
    val rootInputDictInfo =
        scope.calcOnState { inputDictionaryStorage.getRootInfoByIdOrThrow(inputDict.rootInputDictId) }
    val dictNextApplied =
        inputDict.doDictNextImpl(
            scope.ctx,
            keyExtended,
            rootInputDictInfo,
            freshConstantForInput,
            resultSymbol,
            isNext,
            mightBeEqualToPivot,
            dictKeyKind == DictKeyKind.SIGNED_INT,
        )
    val actions =
        dictNextApplied.map {
            TvmStepScopeManager.ActionOnCondition({}, false, ctx.mkAnd(it.constraintSet), it)
        }
    scope.doWithConditions(actions) {
        calcOnState {
            inputDictionaryStorage =
                inputDictionaryStorage.updateRootInputDictionary(
                    inputDict.rootInputDictId,
                    it.newInputDictRootInformation,
                )
        }
        doWithResult(it)
    }
}
