package org.usvm.machine.interpreter

import io.ksmt.sort.KBvSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.bigIntValue
import org.usvm.machine.interpreter.inputdict.EqualToOneOf
import org.usvm.machine.interpreter.inputdict.InputDict
import org.usvm.machine.state.DictId
import org.usvm.machine.state.allocatedDictContainsKey
import org.usvm.machine.state.builderToCell
import org.usvm.machine.state.dictGetValue
import org.usvm.machine.state.dictKeyEntries
import org.usvm.machine.state.preloadDataBitsFromCellWithoutChecks
import org.usvm.machine.state.readCellRef
import org.usvm.machine.types.TvmType
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeSubExpr
import org.usvm.model.UModelBase
import org.usvm.sizeSort
import org.usvm.test.resolver.TvmTestBuilderValue
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestDictCellValue
import org.usvm.test.resolver.TvmTestIntegerValue
import org.usvm.test.resolver.TvmTestReferenceValue
import org.usvm.test.resolver.TvmTestSliceValue
import org.usvm.test.resolver.TvmTestStateResolver
import org.usvm.test.resolver.endCell
import org.usvm.test.resolver.truncateSliceCell
import java.math.BigInteger

class TvmValueFixator(
    private val resolver: TvmTestStateResolver,
    private val ctx: TvmContext,
    private val structuralConstraintsOnly: Boolean,
) {
    fun fixateConcreteValue(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
    ): UBoolExpr? {
        val value = resolver.resolveRef(ref)
        return fixateConcreteValue(scope, ref, value)
    }

    private fun fixateConcreteValue(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        value: TvmTestReferenceValue,
    ): UBoolExpr? =
        when (value) {
            is TvmTestDataCellValue -> {
                fixateConcreteValueForDataCell(scope, ref, value)
            }

            is TvmTestSliceValue -> {
                fixateConcreteValueForSlice(scope, ref, value)
            }

            is TvmTestDictCellValue -> {
                fixateConcreteValueForDictCell(scope, ref, value)
            }

            is TvmTestBuilderValue -> {
                fixateConcreteValueForBuilder(scope, ref, value)
            }
        }

    private fun fixateConcreteValueForBuilder(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        value: TvmTestBuilderValue,
    ): UBoolExpr? {
        require(ref is UConcreteHeapRef) {
            "Unexpected non-concrete builder ref $ref"
        }

        val cellRef = scope.builderToCell(ref)
        return fixateConcreteValueForDataCell(scope, cellRef, value.endCell())
    }

    private fun fixateConcreteValueForSlice(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        value: TvmTestSliceValue,
    ): UBoolExpr? =
        with(ctx) {
            val dataPosSymbolic =
                scope.calcOnState {
                    fieldManagers.cellDataLengthFieldManager.readSliceDataPos(this, ref)
                }
            val refPosSymbolic =
                scope.calcOnState {
                    memory.readField(ref, TvmContext.sliceRefPosField, sizeSort)
                }
            val cellRef = scope.calcOnState { memory.readField(ref, TvmContext.sliceCellField, addressSort) }

            fixateConcreteValueForDataCell(
                scope,
                cellRef,
                truncateSliceCell(value),
                dataPosSymbolic,
                refPosSymbolic,
            )
        }

    private fun fixateConcreteValueForDataCell(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        value: TvmTestDataCellValue,
        dataOffset: UExpr<TvmSizeSort> = ctx.zeroSizeExpr,
        refsOffset: UExpr<TvmSizeSort> = ctx.zeroSizeExpr,
    ): UBoolExpr? =
        with(ctx) {
            val childrenCond =
                value.refs.foldIndexed(trueExpr as UBoolExpr) { index, acc, child ->
                    val childRef = scope.calcOnState { readCellRef(ref, mkSizeAddExpr(mkSizeExpr(index), refsOffset)) }
                    val currentConstraint =
                        fixateConcreteValue(scope, childRef, child)
                            ?: return@with null
                    acc and currentConstraint
                }

            val symbolicRefNumber =
                scope.calcOnState {
                    mkSizeSubExpr(
                        fieldManagers.cellRefsLengthFieldManager.readCellRefLength(this, ref),
                        refsOffset,
                    )
                }
            val refCond = symbolicRefNumber eq mkSizeExpr(value.refs.size)

            val dataCond =
                if (!structuralConstraintsOnly) {
                    val symbolicDataLength =
                        scope.calcOnState {
                            mkSizeSubExpr(
                                fieldManagers.cellDataLengthFieldManager.readCellDataLength(this, ref),
                                dataOffset,
                            )
                        }

                    val bitsCond =
                        if (value.data.isEmpty()) {
                            trueExpr
                        } else {
                            val symbolicData =
                                scope.preloadDataBitsFromCellWithoutChecks(ref, dataOffset, value.data.length)
                                    ?: return@with null

                            val concreteData = mkBv(BigInteger(value.data, 2), value.data.length.toUInt())
                            (symbolicData eq concreteData)
                        }

                    bitsCond and (symbolicDataLength eq mkSizeExpr(value.data.length))
                } else {
                    trueExpr
                }

            childrenCond and refCond and dataCond
        }

    private fun fixateConcreteValueForDictCell(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        value: TvmTestDictCellValue,
    ): UBoolExpr? =
        with(ctx) {
            val keyLength =
                scope.calcOnState {
                    memory.readField(ref, TvmContext.dictKeyLengthField, sizeSort)
                }
            var result = keyLength eq mkSizeExpr(value.keyLength)

            val model = resolver.model
            val modelRef = model.eval(ref) as UConcreteHeapRef

            result = result and (ref eq modelRef)

            val dictId = DictId(value.keyLength)
            val keySort = mkBvSort(value.keyLength.toUInt())

            val isInput = scope.calcOnState { inputDictionaryStorage.hasInputDictEntryAtRef(ref) }
            if (!isInput) {
                val newConditions =
                    fixateAllocatedDictEntries(scope, model, modelRef, dictId, keySort, ref, value)
                        ?: return@with null
                result = result and mkAnd(newConditions)
            } else {
                val inputDict =
                    scope.calcOnState {
                        val inputDict =
                            inputDictionaryStorage.memory[ref]
                                ?: error("Input dict not found")
                        inputDict
                    }
                val asserts =
                    fixateInputDictEntries(scope, inputDict, ref, dictId, model, value)
                        ?: return@with null
                result = result and mkAnd(asserts)
            }

            return result
        }

    private fun TvmContext.fixateInputDictEntries(
        scope: TvmStepScopeManager,
        inputDict: InputDict,
        ref: UHeapRef,
        dictId: DictId,
        model: UModelBase<TvmType>,
        value: TvmTestDictCellValue,
    ): List<UBoolExpr>? {
        val beforeFixRootInfo =
            scope.calcOnState { inputDictionaryStorage.getRootInfoByIdOrThrow(inputDict.rootInputDictId) }
        val keyEntries = inputDict.getCurrentlyDiscoveredKeys(ctx, beforeFixRootInfo)
        // `cs` (first arg) is discarded as the conditions are trivially true
        val (_, quantifiers) =
            beforeFixRootInfo.addLazyUniversalConstraint(
                ctx,
                EqualToOneOf(keyEntries, inputDict.modifications),
            )
        val afterFixRootInfo = beforeFixRootInfo.copy(lazyUniversalQuantifierConstraints = quantifiers)
        scope.calcOnState {
            inputDictionaryStorage.updateRootInputDictionary(
                inputDict.rootInputDictId,
                afterFixRootInfo,
            )
        }
        val asserts = mutableListOf<UBoolExpr>()
        for ((key, condition) in keyEntries) {
            val entryValue =
                scope.calcOnState {
                    dictGetValue(ref, dictId, key.expr)
                }

            val concreteKey = TvmTestIntegerValue(model.eval(key.expr).bigIntValue())

            val keyConstraint = model.eval(key.expr) eq key.expr

            val concreteValue = value.entries[concreteKey]
            asserts.add(keyConstraint)
            if (concreteValue == null) {
                asserts.add(condition.not())
            } else {
                val valueConstraint =
                    fixateConcreteValue(scope, entryValue, concreteValue)
                        ?: return null
                asserts.add(condition)
                asserts.add(valueConstraint)
            }
        }
        return asserts
    }

    private fun TvmContext.fixateAllocatedDictEntries(
        scope: TvmStepScopeManager,
        model: UModelBase<TvmType>,
        modelRef: UConcreteHeapRef,
        dictId: DictId,
        keySort: KBvSort,
        ref: UHeapRef,
        value: TvmTestDictCellValue,
    ): List<UBoolExpr>? {
        val asserts = mutableListOf<UBoolExpr>()
        val entries =
            scope.calcOnState {
                dictKeyEntries(model, memory, modelRef, dictId, keySort)
            }

        entries.forEach { entry ->
            val key = entry.setElement
            val keyContains =
                scope.calcOnState {
                    allocatedDictContainsKey(ref, dictId, key)
                }
            val entryValue =
                scope.calcOnState {
                    dictGetValue(ref, dictId, key)
                }

            val concreteKey = TvmTestIntegerValue(model.eval(key).bigIntValue())

            val keyConstraint = model.eval(key) eq key

            val concreteValue = value.entries[concreteKey]
            asserts.add(keyConstraint)
            if (concreteValue == null) {
                asserts.add(keyContains.not())
            } else {
                val valueConstraint =
                    fixateConcreteValue(scope, entryValue, concreteValue)
                        ?: return null
                asserts.add(keyContains)
                asserts.add(valueConstraint)
            }
        }
        return asserts
    }
}
