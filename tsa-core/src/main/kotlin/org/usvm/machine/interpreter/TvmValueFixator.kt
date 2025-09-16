package org.usvm.machine.interpreter

import io.ksmt.expr.KInterpretedValue
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.bigIntValue
import org.usvm.machine.state.ConcreteSet
import org.usvm.machine.state.DictId
import org.usvm.machine.state.TvmFixationMemoryValues
import org.usvm.machine.state.builderToCell
import org.usvm.machine.state.dictGetValue
import org.usvm.machine.state.dictKeyEntries
import org.usvm.machine.state.preloadDataBitsFromCellWithoutChecks
import org.usvm.machine.state.readCellRef
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
    nullRef: UHeapRef,
) {
    private val emptyFixationValues = TvmFixationMemoryValues.empty(nullRef)

    fun fixateConcreteValue(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
    ): Pair<UBoolExpr, TvmFixationMemoryValues>? {
        val value = resolver.resolveRef(ref)
        return fixateConcreteValue(scope, ref, value)
    }

    private fun fixateConcreteValue(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        value: TvmTestReferenceValue,
    ): Pair<UBoolExpr, TvmFixationMemoryValues>? =
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
    ): Pair<UBoolExpr, TvmFixationMemoryValues>? {
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
    ): Pair<UBoolExpr, TvmFixationMemoryValues>? =
        with(ctx) {
            val dataPosSymbolic =
                scope.calcOnState {
                    memory.readField(ref, TvmContext.sliceDataPosField, sizeSort)
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
    ): Pair<UBoolExpr, TvmFixationMemoryValues>? =
        with(ctx) {
            val (childrenGuard, childrenValues) =
                value.refs.foldIndexed(
                    (trueExpr as UBoolExpr) to emptyFixationValues
                ) { index, (accGuard, accValues), child ->
                    val childRef =
                        scope.calcOnState {
                            readCellRef(ref, mkSizeAddExpr(mkSizeExpr(index), refsOffset))
                        }
                    val (curGuard, curValues) =
                        fixateConcreteValue(scope, childRef, child)
                            ?: return@with null
                    (accGuard and curGuard) to (accValues.union(curValues))
                }

            val symbolicRefNumber =
                scope.calcOnState {
                    mkSizeSubExpr(
                        memory.readField(ref, TvmContext.cellRefsLengthField, sizeSort),
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

            (childrenGuard and refCond and dataCond) to childrenValues
        }

    private fun fixateConcreteValueForDictCell(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        value: TvmTestDictCellValue,
    ): Pair<UBoolExpr, TvmFixationMemoryValues>? =
        with(ctx) {
            val keyLength =
                scope.calcOnState {
                    memory.readField(ref, TvmContext.dictKeyLengthField, sizeSort)
                }
            var resultGuard = keyLength eq mkSizeExpr(value.keyLength)

            val modelRef = resolver.eval(ref) as UConcreteHeapRef

            resultGuard = resultGuard and (ref eq modelRef)

            val dictId = DictId(value.keyLength)
            val keySort = mkBvSort(value.keyLength.toUInt())

            val possibleEntries =
                scope.calcOnState {
                    dictKeyEntries(resolver, modelRef, dictId, keySort)
                }

            var childFixValues = emptyFixationValues

            val setKeys = mutableSetOf<KInterpretedValue<UBvSort>>()

            possibleEntries.forEach { entry ->
                val key = entry.setElement

                val concreteKey = TvmTestIntegerValue(resolver.eval(key).bigIntValue())
                val concreteValue = value.entries[concreteKey]

                if (concreteValue != null) {
                    setKeys.add(resolver.eval(key) as KInterpretedValue<UBvSort>)

                    val entryValue =
                        scope.calcOnState {
                            dictGetValue(ref, dictId, key)
                        }
                    val (valueGuard, valueFixValues) =
                        fixateConcreteValue(scope, entryValue, concreteValue)
                            ?: return@with null

                    resultGuard = resultGuard and valueGuard
                    childFixValues = childFixValues.union(valueFixValues)
                }
            }

            val curSet =
                ConcreteSet(
                    ref = modelRef,
                    dictId = dictId,
                    elements = setKeys
                )

            val nullRef = scope.calcOnState { nullRef }

            val curFixValues =
                TvmFixationMemoryValues(
                    setOf(curSet),
                    nullRef = nullRef
                )

            return resultGuard to curFixValues.union(childFixValues)
        }
}
