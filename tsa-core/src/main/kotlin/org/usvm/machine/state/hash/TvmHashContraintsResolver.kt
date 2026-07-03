package org.usvm.machine.state.hash

import io.ksmt.expr.KBvAndExpr
import io.ksmt.expr.KBvOrExpr
import io.ksmt.expr.KEqExpr
import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBvSort
import io.ksmt.utils.uncheckedCast
import mu.KLogging
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UExprTransformer
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UIsSubtypeExpr
import org.usvm.UIsSupertypeExpr
import org.usvm.UNullRef
import org.usvm.URegisterReading
import org.usvm.USort
import org.usvm.UTrackedSymbol
import org.usvm.collection.array.UAllocatedArrayReading
import org.usvm.collection.array.UInputArrayReading
import org.usvm.collection.array.length.UInputArrayLengthReading
import org.usvm.collection.field.UInputFieldReading
import org.usvm.collection.map.length.UInputMapLengthReading
import org.usvm.collection.map.primitive.UAllocatedMapReading
import org.usvm.collection.map.primitive.UInputMapReading
import org.usvm.collection.map.ref.UAllocatedRefMapWithInputKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithAllocatedKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithInputKeysReading
import org.usvm.collection.set.primitive.UAllocatedSetReading
import org.usvm.collection.set.primitive.UInputSetReading
import org.usvm.collection.set.ref.UAllocatedRefSetWithInputElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithAllocatedElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithInputElementsReading
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.intblast.TvmMultiplication
import org.usvm.machine.intblast.TvmSignedDivision
import org.usvm.machine.intblast.TvmSignedModulo
import org.usvm.machine.intblast.TvmTransformer
import org.usvm.machine.state.TvmPathConstraints
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.assertDataCellType
import org.usvm.machine.state.extractFullCellIfItIsConcrete
import org.usvm.machine.state.killCurrentState
import org.usvm.machine.state.makeCellToSliceTlbNoFork
import org.usvm.machine.state.sliceLoadRefNoForkNoUnderflowCHeck
import org.usvm.machine.state.slicesAreEqual
import org.usvm.machine.types.TvmDataCellType
import org.usvm.machine.types.TvmType
import org.usvm.machine.types.getPossibleTypes
import org.usvm.mkSizeExpr
import org.usvm.regions.Region
import org.usvm.test.resolver.TvmTestCellValue
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestStateResolver
import org.usvm.utils.groupIntoParts
import kotlin.collections.map

class TvmHashConstraintsResolver(
    val scope: TvmStepScopeManager,
) {
    companion object {
        private val logger = object : KLogging() {}.logger
    }

    fun generateNewPathConstraints(): TvmPathConstraints? {
        val state = scope.calcOnState { this }
        val transformer = HashEqualityTransformer(scope.ctx, scope, state)

        var changed = false
        val transformedConstraints =
            state.pathConstraints.tvmConstraintsSequence().toList().map { expr ->
                transformer.apply(expr).also {
                    if (transformer.stateWasKilled) {
                        scope.killCurrentState()
                        return null
                    }
                    if (it != expr) {
                        changed = true
                    }
                }
            }

        if (!changed) {
            return state.pathConstraints
        }

        val pathConstraints =
            TvmPathConstraints(
                scope.ctx,
                MutabilityOwnership(),
                tvmSoftConstraints = state.pathConstraints.tvmSoftConstraints,
                typeConstraints = state.pathConstraints.typeConstraints,
            )
        transformedConstraints.forEach {
            pathConstraints += it
        }

        return pathConstraints
    }

    class HashEqualityTransformer(
        override val ctx: TvmContext,
        val scope: TvmStepScopeManager,
        val state: TvmState,
    ) : UExprTransformer<TvmType, TvmSizeSort>(ctx),
        TvmTransformer {
        var stateWasKilled: Boolean = false

        override fun <Sort : KBvSort> transform(expr: KBvOrExpr<Sort>): UExpr<Sort> =
            transformExprAfterTransformed(expr, expr.arg0, expr.arg1) { l, r ->
                with(ctx.tctx()) {
                    val groups = groupIntoParts(l.uncheckedCast(), r.uncheckedCast())
                    if (groups != null && groups.size > 1) {
                        val propagated = groups.map { mkBvOrExpr(it.first, it.second) }
                        return propagated.reduce { acc, expr -> mkBvConcatExpr(acc, expr) }.uncheckedCast()
                    }
                    mkBvOrExpr(l, r)
                }
            }

        override fun <Sort : KBvSort> transform(expr: KBvAndExpr<Sort>): UExpr<Sort> =
            transformExprAfterTransformed(expr, expr.arg0, expr.arg1) { l, r ->
                with(ctx.tctx()) {
                    val groups = groupIntoParts(l.uncheckedCast(), r.uncheckedCast())
                    if (groups != null && groups.size > 1) {
                        val propagated = groups.map { mkBvAndExpr(it.first, it.second) }
                        return propagated.reduce { acc, expr -> mkBvConcatExpr(acc, expr) }.uncheckedCast()
                    }
                    mkBvAndExpr(l, r)
                }
            }

        private fun transformToCellEquality(
            concreteRef: UConcreteHeapRef,
            refToFixate: UConcreteHeapRef,
        ): UBoolExpr? {
            if (concreteRef == refToFixate) {
                return ctx.trueExpr
            }

            val possibleLhsTypes = state.getPossibleTypes(concreteRef).toList()
            val possibleRhsTypes = state.getPossibleTypes(refToFixate).toList()

            if (TvmDataCellType !in possibleLhsTypes || TvmDataCellType !in possibleRhsTypes) {
                return null
            }

            val resolver = TvmTestStateResolver(ctx, state.tvmModels.first(), state)
            val value =
                resolver.resolveRef(concreteRef) as? TvmTestCellValue
                    ?: error("Unexpected resolver value")

            // no support for dicts yet
            if (value !is TvmTestDataCellValue) {
                return null
            }

            scope.assertDataCellType(concreteRef)
                ?: error("Unexpected unsat")
            scope.assertDataCellType(refToFixate)
                ?: error("Unexpected unsat")

            var slice1 = makeCellToSliceTlbNoFork(scope, concreteRef)
            var slice2 = makeCellToSliceTlbNoFork(scope, refToFixate)

            var result =
                scope.slicesAreEqual(slice1, slice2)
                    ?: run {
                        stateWasKilled = true
                        return null
                    }

            val refs =
                scope.calcOnState {
                    fieldManagers.cellRefsLengthFieldManager.readCellRefLength(this, refToFixate)
                }

            result = ctx.mkAnd(result, ctx.mkEq(refs, ctx.mkSizeExpr(value.refs.size)))

            for (idx in 0..<value.refs.size) {
                val (newSlice1, ref1) =
                    sliceLoadRefNoForkNoUnderflowCHeck(scope, slice1)
                        ?: run {
                            stateWasKilled = true
                            return null
                        }
                val (newSlice2, ref2) =
                    sliceLoadRefNoForkNoUnderflowCHeck(scope, slice2)
                        ?: run {
                            stateWasKilled = true
                            return null
                        }

                if (ref1.value !is UConcreteHeapRef || ref2.value !is UConcreteHeapRef) {
                    return null
                }

                if (newSlice1 !is UConcreteHeapRef || newSlice2 !is UConcreteHeapRef) {
                    return null
                }

                val childCond =
                    transformToCellEquality(ref1.value, ref2.value)
                        ?: return null

                result = ctx.mkAnd(result, childCond)

                slice1 = newSlice1
                slice2 = newSlice2
            }

            return result
        }

        private fun processHashEquality(
            l: UExpr<*>,
            r: UExpr<*>,
        ): UBoolExpr? {
            if (l is TvmHashSymbol && r is TvmHashSymbol) {
                logger.debug("Hash equality in path constraints")
                val possibleLhsTypes = state.getPossibleTypes(l.ref).toList()
                val possibleRhsTypes = state.getPossibleTypes(r.ref).toList()
                if (TvmDataCellType in possibleLhsTypes && TvmDataCellType in possibleRhsTypes) {
                    val lhsValue = state.extractFullCellIfItIsConcrete(l.ref)
                    val rhsValue = state.extractFullCellIfItIsConcrete(r.ref)
                    if (lhsValue != null) {
                        return transformToCellEquality(l.ref, r.ref)?.also {
                            logger.debug("Transformed to cell equality")
                        }
                    }
                    if (rhsValue != null) {
                        return transformToCellEquality(r.ref, l.ref)?.also {
                            logger.debug("Transformed to cell equality")
                        }
                    }
                }
            }
            return null
        }

        override fun <T : USort> transform(expr: KEqExpr<T>): KExpr<KBoolSort> =
            transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
                if (l.sort is KBvSort && r.sort is KBvSort) {
                    val groups = groupIntoParts(l.uncheckedCast(), r.uncheckedCast())
                    if (groups != null && groups.size > 1) {
                        val propagated =
                            groups.map {
                                processHashEquality(it.first, it.second)
                                    ?: ctx.mkEq(it.first, it.second)
                            }
                        return propagated
                            .reduce { acc, expr ->
                                ctx.mkAnd(acc, expr)
                            }.uncheckedCast()
                    }
                }

                processHashEquality(l, r)
                    ?: ctx.mkEq(l, r)
            }

        override fun transform(expr: TvmHashSymbol): UExpr<UBvSort> = expr

        override fun <Sort : KBvSort> transform(expr: TvmSignedDivision<Sort>): UExpr<Sort> = expr

        override fun <Sort : KBvSort> transform(expr: TvmMultiplication<Sort>): UExpr<Sort> = expr

        override fun <Sort : KBvSort> transform(expr: TvmSignedModulo<Sort>): UExpr<Sort> = expr

        override fun <Sort : USort> transform(expr: URegisterReading<Sort>): KExpr<Sort> = expr

        override fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): KExpr<Sort> = expr

        override fun <Sort : USort> transform(expr: UTrackedSymbol<Sort>): UExpr<Sort> = expr

        override fun transform(expr: UNullRef): KExpr<UAddressSort> = expr

        override fun transform(expr: UConcreteHeapRef): KExpr<UAddressSort> = expr

        override fun transform(expr: UIsSubtypeExpr<TvmType>): KExpr<KBoolSort> = expr

        override fun transform(expr: UIsSupertypeExpr<TvmType>): KExpr<KBoolSort> = expr

        override fun transform(expr: UInputArrayLengthReading<TvmType, TvmSizeSort>): KExpr<TvmSizeSort> = expr

        override fun <Sort : USort> transform(expr: UInputArrayReading<TvmType, Sort, TvmSizeSort>): KExpr<Sort> = expr

        override fun <Sort : USort> transform(expr: UAllocatedArrayReading<TvmType, Sort, TvmSizeSort>): KExpr<Sort> =
            expr

        override fun <Field, Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): KExpr<Sort> = expr

        override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
            expr: UAllocatedMapReading<TvmType, KeySort, Sort, Reg>,
        ): KExpr<Sort> = expr

        override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
            expr: UInputMapReading<TvmType, KeySort, Sort, Reg>,
        ): KExpr<Sort> = expr

        override fun <Sort : USort> transform(expr: UAllocatedRefMapWithInputKeysReading<TvmType, Sort>): UExpr<Sort> =
            expr

        override fun <Sort : USort> transform(expr: UInputRefMapWithAllocatedKeysReading<TvmType, Sort>): UExpr<Sort> =
            expr

        override fun <Sort : USort> transform(expr: UInputRefMapWithInputKeysReading<TvmType, Sort>): UExpr<Sort> = expr

        override fun transform(expr: UInputMapLengthReading<TvmType, TvmSizeSort>): KExpr<TvmSizeSort> = expr

        override fun <ElemSort : USort, Reg : Region<Reg>> transform(
            expr: UAllocatedSetReading<TvmType, ElemSort, Reg>,
        ): UBoolExpr = expr

        override fun <ElemSort : USort, Reg : Region<Reg>> transform(
            expr: UInputSetReading<TvmType, ElemSort, Reg>,
        ): UBoolExpr = expr

        override fun transform(expr: UAllocatedRefSetWithInputElementsReading<TvmType>): UBoolExpr = expr

        override fun transform(expr: UInputRefSetWithAllocatedElementsReading<TvmType>): UBoolExpr = expr

        override fun transform(expr: UInputRefSetWithInputElementsReading<TvmType>): UBoolExpr = expr
    }
}
