package org.usvm.machine.state.hash

import io.ksmt.sort.KBvSort
import io.ksmt.utils.uncheckedCast
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
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.intblast.TvmMultiplication
import org.usvm.machine.intblast.TvmSignedDivision
import org.usvm.machine.intblast.TvmSignedModulo
import org.usvm.machine.intblast.TvmTransformer
import org.usvm.machine.state.TsaAccountIdSymbol
import org.usvm.machine.types.TvmType
import org.usvm.regions.Region

open class TvmDefaultTransformer(
    ctx: TvmContext,
) : UExprTransformer<TvmType, TvmSizeSort>(ctx),
    TvmTransformer {
    override fun <Sort : USort> transform(expr: URegisterReading<Sort>): UExpr<Sort> = expr

    override fun <Field, Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): UExpr<Sort> = expr

    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<TvmType, Sort, TvmSizeSort>): UExpr<Sort> = expr

    override fun <Sort : USort> transform(expr: UInputArrayReading<TvmType, Sort, TvmSizeSort>): UExpr<Sort> = expr

    override fun transform(expr: UInputArrayLengthReading<TvmType, TvmSizeSort>): UExpr<TvmSizeSort> = expr

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedMapReading<TvmType, KeySort, Sort, Reg>,
    ): UExpr<Sort> = expr

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UInputMapReading<TvmType, KeySort, Sort, Reg>,
    ): UExpr<Sort> = expr

    override fun <Sort : USort> transform(expr: UAllocatedRefMapWithInputKeysReading<TvmType, Sort>): UExpr<Sort> = expr

    override fun <Sort : USort> transform(expr: UInputRefMapWithAllocatedKeysReading<TvmType, Sort>): UExpr<Sort> = expr

    override fun <Sort : USort> transform(expr: UInputRefMapWithInputKeysReading<TvmType, Sort>): UExpr<Sort> = expr

    override fun transform(expr: UInputMapLengthReading<TvmType, TvmSizeSort>): UExpr<TvmSizeSort> = expr

    override fun <ElemSort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedSetReading<TvmType, ElemSort, Reg>,
    ): UBoolExpr = expr

    override fun <ElemSort : USort, Reg : Region<Reg>> transform(
        expr: UInputSetReading<TvmType, ElemSort, Reg>,
    ): UBoolExpr = expr

    override fun transform(expr: UAllocatedRefSetWithInputElementsReading<TvmType>): UBoolExpr = expr

    override fun transform(expr: UInputRefSetWithAllocatedElementsReading<TvmType>): UBoolExpr = expr

    override fun transform(expr: UInputRefSetWithInputElementsReading<TvmType>): UBoolExpr = expr

    override fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): UExpr<Sort> = expr

    override fun <Sort : USort> transform(expr: UTrackedSymbol<Sort>): UExpr<Sort> = expr

    override fun transform(expr: UIsSubtypeExpr<TvmType>): UBoolExpr = expr

    override fun transform(expr: UIsSupertypeExpr<TvmType>): UBoolExpr = expr

    override fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort> = expr

    override fun transform(expr: UNullRef): UExpr<UAddressSort> = expr

    override fun <Sort : KBvSort> transform(expr: TvmSignedDivision<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            ctx.tctx().mkTvmSignedDiv(l, r)
        }

    override fun <Sort : KBvSort> transform(expr: TvmMultiplication<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            ctx.tctx().mkTvmMulNoSimplify(l, r)
        }

    override fun <Sort : KBvSort> transform(expr: TvmSignedModulo<Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.lhs, expr.rhs) { l, r ->
            ctx.tctx().mkTvmSignedMod(l, r)
        }

    override fun transform(expr: TvmSymbolicHashSymbol): UExpr<UBvSort> =
        (transform(expr.fallbackExpr) as? UExpr<*>)?.uncheckedCast()
            ?: error("failed transformation")

    override fun transform(expr: TvmConstantHashSymbol): UExpr<UBvSort> =
        (transform(expr.fallbackExpr) as? UExpr<*>)?.uncheckedCast()
            ?: error("failed transformation")

    override fun transform(expr: TsaAccountIdSymbol): UExpr<UBvSort> =
        transformExprAfterTransformed(
            expr,
            expr.isStateInit,
            expr.boundStateInitHash,
            expr.symbolicAccountId,
            expr.code,
            expr.data,
        ) { newIsStateInit, newBoundStateInitHash, newSymbolicAccountId, newCode, newData ->
            ctx
                .tctx()
                .mkTsaAccountIdSymbol(
                    newIsStateInit,
                    newBoundStateInitHash as? TvmSymbolicHashSymbol
                        ?: error("newBoundStateInitHash=$newBoundStateInitHash expected to be TvmSymbolicHashSymbol"),
                    newSymbolicAccountId,
                    newData,
                    newCode,
                )
        }
}
