package org.usvm.machine.state.hash

import io.ksmt.expr.KBitVecValue
import io.ksmt.expr.KBvAndExpr
import io.ksmt.expr.KBvOrExpr
import io.ksmt.expr.KEqExpr
import io.ksmt.expr.KExpr
import io.ksmt.expr.transformer.KNonRecursiveTransformerBase
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBvSort
import io.ksmt.utils.uncheckedCast
import mu.KLogging
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.intblast.TvmMultiplication
import org.usvm.machine.intblast.TvmSignedDivision
import org.usvm.machine.intblast.TvmSignedModulo
import org.usvm.machine.intblast.TvmTransformer
import org.usvm.machine.state.TsaAccountIdSymbol
import org.usvm.machine.state.TvmPathConstraints
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.assertBuilderType
import org.usvm.machine.state.assertDataCellType
import org.usvm.machine.state.extractFullCellIfItIsConcrete
import org.usvm.machine.state.killCurrentState
import org.usvm.machine.state.makeCellToSliceTlbNoFork
import org.usvm.machine.state.readCellRefsCount
import org.usvm.machine.state.sliceLoadRefNoForkNoUnderflowCHeck
import org.usvm.machine.state.slicesDataBitsAreEqual
import org.usvm.machine.types.TvmBuilderType
import org.usvm.machine.types.TvmDataCellType
import org.usvm.machine.types.asCellRef
import org.usvm.machine.types.getPossibleTypes
import org.usvm.mkSizeExpr
import org.usvm.test.resolver.TvmTestBuilderValue
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestDictCellValue
import org.usvm.test.resolver.TvmTestReferenceValue
import org.usvm.test.resolver.TvmTestSliceValue
import org.usvm.test.resolver.TvmTestStateResolver
import org.usvm.utils.groupIntoParts
import org.usvm.utils.intValueOrNull

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
    ) : DefaultUExprTransformer(ctx),
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
            fullyConcreteRef: UConcreteHeapRef,
            refToFixate: UConcreteHeapRef,
        ): UBoolExpr? {
            if (fullyConcreteRef == refToFixate) {
                return ctx.trueExpr
            }

            val possibleLhsTypes = state.getPossibleTypes(fullyConcreteRef).toList()
            val possibleRhsTypes = state.getPossibleTypes(refToFixate).toList()

            // TODO: consider the case where a either ref could be a slice
            val okTypes =
                (TvmDataCellType in possibleLhsTypes || TvmBuilderType in possibleLhsTypes) &&
                    (TvmDataCellType in possibleRhsTypes || TvmBuilderType in possibleRhsTypes)
            if (!okTypes) {
                return null
            }

            val resolver = TvmTestStateResolver(ctx, state.tvmModels.first(), state)
            val value =
                resolver
                    .resolveRef(fullyConcreteRef)
                    .convertToCellValuePreservingHash()
                    ?: error("Unexpected resolver value")

            assertTypeCellOrBuilderType(fullyConcreteRef)
            assertTypeCellOrBuilderType(refToFixate)

            var slice1 = makeCellToSliceTlbNoFork(scope, fullyConcreteRef)
            var slice2 = makeCellToSliceTlbNoFork(scope, refToFixate)

            var result =
                scope.slicesDataBitsAreEqual(slice1, slice2)
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

        private fun TvmTestReferenceValue.convertToCellValuePreservingHash(): TvmTestDataCellValue? {
            return when (this) {
                is TvmTestBuilderValue -> {
                    TvmTestDataCellValue(
                        data = this.data,
                        refs = this.refs.map { it.convertToCellValuePreservingHash() ?: return null },
                    )
                }

                is TvmTestDataCellValue -> {
                    this
                }

                is TvmTestDictCellValue -> {
                    null
                }

                is TvmTestSliceValue -> {
                    null
                }
            }
        }

        private fun assertTypeCellOrBuilderType(fullyConcreteRef: UConcreteHeapRef) {
            if (TvmDataCellType in state.getPossibleTypes(fullyConcreteRef)) {
                scope.assertDataCellType(fullyConcreteRef)
                    ?: error("Unexpected unsat")
            } else if (TvmBuilderType in state.getPossibleTypes(fullyConcreteRef)) {
                scope.assertBuilderType(fullyConcreteRef)
                    ?: error("Unexpected unsat")
            }
        }

        private fun transformToCellEqualityWhenOneRefsLengthIsConcrete(
            withConcreteRefsLength: UConcreteHeapRef,
            rhsCell: UConcreteHeapRef,
        ): UBoolExpr? {
            if (withConcreteRefsLength == rhsCell) {
                return ctx.trueExpr
            }

            val possibleLhsTypes = state.getPossibleTypes(withConcreteRefsLength).toList()
            val possibleRhsTypes = state.getPossibleTypes(rhsCell).toList()

            val okTypes =
                (TvmDataCellType in possibleRhsTypes || TvmBuilderType in possibleRhsTypes) &&
                    (TvmDataCellType in possibleLhsTypes || TvmBuilderType in possibleLhsTypes)
            if (!okTypes) {
                return null
            }
            val lhsRefsCount =
                state.readCellRefsCount(withConcreteRefsLength.asCellRef()).intValueOrNull
                    ?: return null

            assertTypeCellOrBuilderType(withConcreteRefsLength)
            assertTypeCellOrBuilderType(rhsCell)

            var slice1 = makeCellToSliceTlbNoFork(scope, withConcreteRefsLength)
            var slice2 = makeCellToSliceTlbNoFork(scope, rhsCell)

            val result = mutableListOf<UBoolExpr>()
            result.add(
                scope.slicesDataBitsAreEqual(slice1, slice2)
                    ?: run {
                        stateWasKilled = true
                        return null
                    },
            )

            val rhsRefsCount =
                scope.calcOnState {
                    fieldManagers.cellRefsLengthFieldManager.readCellRefLength(this, rhsCell)
                }

            result.add(ctx.mkEq(rhsRefsCount, ctx.mkSizeExpr(lhsRefsCount)))

            for (idx in 0..<lhsRefsCount) {
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
                val ref1Refs = state.readCellRefsCount(ref1).intValueOrNull
                val ref2Refs = state.readCellRefsCount(ref2).intValueOrNull

                val childCond =
                    if (ref1Refs != null) {
                        transformToCellEquality(ref1.value, ref2.value)
                            ?: return null
                    } else if (ref2Refs != null) {
                        transformToCellEquality(ref2.value, ref1.value)
                            ?: return null
                    } else {
                        return null
                    }

                result.add(childCond)

                slice1 = newSlice1
                slice2 = newSlice2
            }

            return ctx.mkAnd(result)
        }

        /**
         * - `TsaAccountId == hash -> isStateInit && (boundStateInitHash == hash)`
         * - `TsaAccountId == constValue -> !isStateInit && (symbolicAccountId == constValue)`.
         * - otherwise -> leave as is (will be rewritten in the translator)
         */
        private fun tryRewriteEqualityWithTsaAccountId(
            l: UExpr<*>,
            r: UExpr<*>,
        ): UBoolExpr? {
            val (accountId, other) =
                if (l is TsaAccountIdSymbol) {
                    l to r
                } else if (r is TsaAccountIdSymbol) {
                    r to l
                } else {
                    return null
                }
            // not null iff other is hash
            val rewrittenHashEquality = tryRewriteHashEquality(accountId.boundStateInitHash, other)
            return with(ctx) {
                if (rewrittenHashEquality != null) {
                    accountId.isStateInit and rewrittenHashEquality
                } else if (other is KBitVecValue<*>) {
                    mkNot(accountId.isStateInit) and
                        mkEq(accountId.symbolicAccountId, other.uncheckedCast())
                } else {
                    null
                }
            }
        }

        private fun tryRewriteHashEquality(
            l: UExpr<*>,
            r: UExpr<*>,
        ): UBoolExpr? {
            if (l is TvmHashSymbol && r is TvmHashSymbol) {
                logger.debug("Hash equality in path constraints")
                val possibleLhsTypes = state.getPossibleTypes(l.ref).toList()
                val possibleRhsTypes = state.getPossibleTypes(r.ref).toList()
                if ((TvmDataCellType in possibleLhsTypes || TvmBuilderType in possibleLhsTypes) &&
                    (TvmDataCellType in possibleRhsTypes || TvmBuilderType in possibleRhsTypes)
                ) {
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
                    val lhsRefs = state.readCellRefsCount(l.ref.asCellRef()).intValueOrNull
                    val rhsRefs = state.readCellRefsCount(r.ref.asCellRef()).intValueOrNull
                    if (lhsRefs != null) {
                        return transformToCellEqualityWhenOneRefsLengthIsConcrete(l.ref, r.ref)
                    } else if (rhsRefs != null) {
                        return transformToCellEqualityWhenOneRefsLengthIsConcrete(r.ref, l.ref)
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
                                tryRewriteEqualityWithTsaAccountId(it.first, it.second)
                                    ?: tryRewriteHashEquality(it.first, it.second)
                                    ?: ctx.mkEq(it.first, it.second)
                            }
                        return propagated
                            .reduce { acc, expr ->
                                ctx.mkAnd(acc, expr)
                            }.uncheckedCast()
                    }
                }

                tryRewriteEqualityWithTsaAccountId(l, r)
                    ?: tryRewriteHashEquality(l, r)
                    ?: ctx.mkEq(l, r)
            }

        override fun <Sort : KBvSort> transform(expr: TvmSignedDivision<Sort>): UExpr<Sort> = transformDefault(expr)

        override fun <Sort : KBvSort> transform(expr: TvmMultiplication<Sort>): UExpr<Sort> = transformDefault(expr)

        override fun <Sort : KBvSort> transform(expr: TvmSignedModulo<Sort>): UExpr<Sort> = transformDefault(expr)

        override fun transform(expr: TvmSymbolicHashSymbol): UExpr<UBvSort> = expr

        override fun transform(expr: TvmConstantHashSymbol): UExpr<UBvSort> = expr

        override fun transform(expr: TsaAccountIdSymbol): UExpr<UBvSort> = expr
    }
}

fun <Sort : KBvSort> KNonRecursiveTransformerBase.transformDefault(expr: TvmSignedDivision<Sort>): KExpr<Sort> =
    transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { newLhs, newRhs ->
        ctx.tctx().mkTvmSignedDiv(newLhs, newRhs)
    }

fun <Sort : KBvSort> KNonRecursiveTransformerBase.transformDefault(expr: TvmMultiplication<Sort>): KExpr<Sort> =
    transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { newLhs, newRhs ->
        ctx.tctx().mkTvmMul(newLhs, newRhs)
    }

fun <Sort : KBvSort> KNonRecursiveTransformerBase.transformDefault(expr: TvmSignedModulo<Sort>): KExpr<Sort> =
    transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { newLhs, newRhs ->
        ctx.tctx().mkTvmSignedMod(newLhs, newRhs)
    }
