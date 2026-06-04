package org.usvm.machine.interpreter

import io.ksmt.sort.KBvSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.isTrue
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
import org.usvm.machine.types.memory.readInModelFromTlbFields
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeSubExpr
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
        compareRecursively: Boolean = true,
    ): UBoolExpr? {
        val value = resolver.resolveRef(ref)
        return fixateConcreteValue(scope, ref, value, compareRecursively = compareRecursively)
    }

    private fun fixateConcreteValue(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        value: TvmTestReferenceValue,
        compareRecursively: Boolean = true,
    ): UBoolExpr? =
        when (value) {
            is TvmTestDataCellValue -> {
                fixateConcreteValueForDataCell(scope, ref, value, compareRecursively = compareRecursively)
            }

            is TvmTestSliceValue -> {
                fixateConcreteValueForSlice(scope, ref, value, compareRecursively)
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

    fun fixateConcreteValueForSlice(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        value: TvmTestSliceValue,
        compareRecursively: Boolean = true,
    ): UBoolExpr? =
        with(ctx) {
            val modelRef = resolver.eval(ref) as UConcreteHeapRef

            val dataPosSymbolic =
                scope.calcOnState {
                    fieldManagers.cellDataLengthFieldManager.readSliceDataPos(this, ref)
                }
            val refPosSymbolic =
                scope.calcOnState {
                    memory.readField(ref, TvmContext.sliceRefPosField, sizeSort)
                }
            val cellRef = scope.calcOnState { memory.readField(ref, TvmContext.sliceCellField, addressSort) }

            val tlbStack =
                scope.calcOnState {
                    dataCellInfoStorage.sliceMapper.getTlbStack(modelRef)
                }
            if (tlbStack != null && !tlbStack.lastFrameIsUnknownWithOffset()) {
                val cellLength =
                    scope.calcOnState {
                        fieldManagers.cellDataLengthFieldManager.readCellDataLength(this, cellRef)
                    }
                val symbolicDataLength =
                    mkSizeSubExpr(
                        cellLength,
                        dataPosSymbolic,
                    )

                val modelReadResult = readInModelFromTlbFields(cellRef, resolver, tlbStack, symbolicDataLength)
                val children =
                    modelReadResult.missedSlices.map { (ref, value) ->
                        fixateConcreteValue(scope, ref, value, compareRecursively = compareRecursively)
                            ?: return@with null
                    }

                // TODO: check that modelReadResult matches [value]

                val dataGuard = children.fold(modelReadResult.guard) { acc, cond -> acc and cond }

                val refs = truncateSliceCell(value).refs

                val restGuard =
                    fixateConcreteValueForDataCell(
                        scope,
                        cellRef,
                        TvmTestDataCellValue(data = "", refs),
                        dataOffset = cellLength,
                        refPosSymbolic,
                        compareRecursively = compareRecursively,
                    ) ?: return null

                return dataGuard and restGuard and (ref eq modelRef) and (resolver.eval(cellRef) eq cellRef)
            }

            val cellGuard =
                fixateConcreteValueForDataCell(
                    scope,
                    cellRef,
                    truncateSliceCell(value),
                    dataPosSymbolic,
                    refPosSymbolic,
                ) ?: return null

            (ref eq modelRef) and cellGuard
        }

    private fun fixateConcreteValueForDataCell(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        value: TvmTestDataCellValue,
        dataOffset: UExpr<TvmSizeSort> = ctx.zeroSizeExpr,
        refsOffset: UExpr<TvmSizeSort> = ctx.zeroSizeExpr,
        compareRecursively: Boolean = true,
    ): UBoolExpr? =
        with(ctx) {
            val childrenCond =
                if (compareRecursively) {
                    value.refs.foldIndexed(trueExpr as UBoolExpr) { index, acc, child ->
                        val childRef =
                            scope.calcOnState { readCellRef(ref, mkSizeAddExpr(mkSizeExpr(index), refsOffset)) }
                        val currentConstraint =
                            fixateConcreteValue(scope, childRef, child)
                                ?: return@with null
                        acc and currentConstraint
                    }
                } else {
                    ctx.trueExpr
                }

            val symbolicRefNumber =
                if (compareRecursively) {
                    scope.calcOnState {
                        mkSizeSubExpr(
                            fieldManagers.cellRefsLengthFieldManager.readCellRefLength(this, ref),
                            refsOffset,
                        )
                    }
                } else {
                    ctx.zeroSizeExpr
                }
            val refCond =
                if (compareRecursively) {
                    symbolicRefNumber eq mkSizeExpr(value.refs.size)
                } else {
                    ctx.trueExpr
                }

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
                            val label = resolver.hasDataCellLabel(ref)
                            if (label != null && (dataOffset eq zeroSizeExpr).isTrue) {
                                val modelReadResult = readInModelFromTlbFields(ref, resolver, label.dataCellStructure)
                                val children =
                                    modelReadResult.missedSlices.map { (ref, value) ->
                                        fixateConcreteValue(
                                            scope,
                                            ref,
                                            value,
                                            compareRecursively = compareRecursively,
                                        )
                                            ?: return@with null
                                    }
                                children.fold(modelReadResult.guard) { acc, cond -> acc and cond }
                            } else {
                                val symbolicData =
                                    scope.preloadDataBitsFromCellWithoutChecks(ref, dataOffset, value.data.length)
                                        ?: return@with null

                                val concreteData = mkBv(BigInteger(value.data, 2), value.data.length.toUInt())
                                (symbolicData eq concreteData)
                            }
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

            val isInput = scope.calcOnState { inputDictionaryStorage.hasInputDictEntryAtRef(modelRef) }
            if (!isInput) {
                val newConditions =
                    fixateAllocatedDictEntries(scope, modelRef, dictId, keySort, ref, value)
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
                    fixateInputDictEntries(scope, inputDict, ref, dictId, value)
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
        value: TvmTestDictCellValue,
    ): List<UBoolExpr>? {
        val model = resolver.model
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
        modelRef: UConcreteHeapRef,
        dictId: DictId,
        keySort: KBvSort,
        ref: UHeapRef,
        value: TvmTestDictCellValue,
    ): List<UBoolExpr>? {
        val model = resolver.model
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
