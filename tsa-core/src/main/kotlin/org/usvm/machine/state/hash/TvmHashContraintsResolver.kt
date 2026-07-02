package org.usvm.machine.state.hash

import io.ksmt.expr.KBitVecValue
import io.ksmt.expr.KBvAndExpr
import io.ksmt.expr.KBvOrExpr
import io.ksmt.expr.KEqExpr
import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBvSort
import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UIteExpr
import org.usvm.USort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.intblast.TvmTransformer
import org.usvm.machine.state.TsaAccountId
import org.usvm.machine.state.TvmPathConstraints
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.assertDataCellType
import org.usvm.machine.state.extractFullCellIfItIsConcrete
import org.usvm.machine.state.killCurrentState
import org.usvm.machine.state.makeCellToSliceTlbNoFork
import org.usvm.machine.state.readCellRefsCount
import org.usvm.machine.state.sliceLoadRefNoForkNoUnderflowCHeck
import org.usvm.machine.state.slicesDataBitsAreEqual
import org.usvm.machine.types.TvmDataCellType
import org.usvm.machine.types.asCellRef
import org.usvm.machine.types.getPossibleTypes
import org.usvm.mkSizeExpr
import org.usvm.test.resolver.TvmTestCellValue
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestStateResolver
import org.usvm.utils.groupIntoParts
import org.usvm.utils.intValueOrNull

class TvmHashConstraintsResolver(
    val scope: TvmStepScopeManager,
) {
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
    ) : TvmDefaultTransformer(ctx),
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

            if (TvmDataCellType !in possibleLhsTypes || TvmDataCellType !in possibleRhsTypes) {
                return null
            }

            val resolver = TvmTestStateResolver(ctx, state.tvmModels.first(), state)
            val value =
                resolver.resolveRef(fullyConcreteRef) as? TvmTestCellValue
                    ?: error("Unexpected resolver value")

            // no support for dicts yet
            if (value !is TvmTestDataCellValue) {
                return null
            }

            scope.assertDataCellType(fullyConcreteRef)
                ?: error("Unexpected unsat")
            scope.assertDataCellType(refToFixate)
                ?: error("Unexpected unsat")

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

        private fun transformToCellEqualityAlternative(
            withConcreteRefsLength: UConcreteHeapRef,
            rhsCell: UConcreteHeapRef,
        ): UBoolExpr? {
            if (withConcreteRefsLength == rhsCell) {
                return ctx.trueExpr
            }

            val possibleLhsTypes = state.getPossibleTypes(withConcreteRefsLength).toList()
            val possibleRhsTypes = state.getPossibleTypes(rhsCell).toList()

            if (TvmDataCellType !in possibleLhsTypes || TvmDataCellType !in possibleRhsTypes) {
                return null
            }
            val lhsRefsCount =
                state.readCellRefsCount(withConcreteRefsLength.asCellRef()).intValueOrNull
                    ?: return null

            scope.assertDataCellType(withConcreteRefsLength)
                ?: error("Unexpected unsat")
            scope.assertDataCellType(rhsCell)
                ?: error("Unexpected unsat")

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
                if (ref1.value is UIteExpr<*> || ref2.value is UIteExpr<*>) {
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
                if (l is TsaAccountId) {
                    l to r
                } else if (r is TsaAccountId) {
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
                val possibleLhsTypes = state.getPossibleTypes(l.ref).toList()
                val possibleRhsTypes = state.getPossibleTypes(r.ref).toList()
                if (TvmDataCellType in possibleLhsTypes && TvmDataCellType in possibleRhsTypes) {
                    val lhsValue = state.extractFullCellIfItIsConcrete(l.ref)
                    val rhsValue = state.extractFullCellIfItIsConcrete(r.ref)
                    if (lhsValue != null) {
                        return transformToCellEquality(l.ref, r.ref)
                    }
                    if (rhsValue != null) {
                        return transformToCellEquality(r.ref, l.ref)
                    }
                    val lhsRefs = state.readCellRefsCount(l.ref.asCellRef()).intValueOrNull
                    val rhsRefs = state.readCellRefsCount(r.ref.asCellRef()).intValueOrNull
                    if (lhsRefs != null) {
                        return transformToCellEqualityAlternative(l.ref, r.ref)
                    } else if (rhsRefs != null) {
                        return transformToCellEqualityAlternative(r.ref, l.ref)
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

        override fun transform(expr: TvmSymbolicHashSymbol): UExpr<UBvSort> = expr
    }
}
