package org.usvm.machine.state.hash

import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
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
import org.usvm.machine.types.TvmType
import org.usvm.regions.Region
import org.usvm.solver.UExprTranslator

open class DefaultUExprTransformer(
    ctx: TvmContext,
) : UExprTranslator<TvmType, TvmSizeSort>(ctx) {
    override fun <Sort : USort> transform(expr: URegisterReading<Sort>): UExpr<Sort> = expr

    override fun <Field, Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.address) { newRef ->
            ctx.tctx().mkInputFieldReading(expr.collection, newRef)
        }

    override fun <ElemSort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedSetReading<TvmType, ElemSort, Reg>,
    ): UBoolExpr =
        transformExprAfterTransformed(expr, expr.element) { newElement ->
            val translator =
                setRegionDecoder(expr.collection.collectionId)
                    .allocatedSetTranslator(expr.collection.collectionId)
            translator.translateReading(expr.collection, newElement)
        }

    override fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort> = expr

    override fun <Sort : USort> transform(expr: UTrackedSymbol<Sort>): UExpr<Sort> = expr

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedMapReading<TvmType, KeySort, Sort, Reg>,
    ): UExpr<Sort> = error("unreachable")

    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<TvmType, Sort, TvmSizeSort>): UExpr<Sort> =
        error("unreachable")

    override fun <Sort : USort> transform(expr: UInputArrayReading<TvmType, Sort, TvmSizeSort>): UExpr<Sort> =
        error("unreachable")

    override fun transform(expr: UInputArrayLengthReading<TvmType, TvmSizeSort>): UExpr<TvmSizeSort> =
        error("unreachable")

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UInputMapReading<TvmType, KeySort, Sort, Reg>,
    ): UExpr<Sort> = error("unreachable")

    override fun <Sort : USort> transform(expr: UAllocatedRefMapWithInputKeysReading<TvmType, Sort>): UExpr<Sort> =
        error("unreachable")

    override fun <Sort : USort> transform(expr: UInputRefMapWithAllocatedKeysReading<TvmType, Sort>): UExpr<Sort> =
        error("unreachable")

    override fun <Sort : USort> transform(expr: UInputRefMapWithInputKeysReading<TvmType, Sort>): UExpr<Sort> =
        error("unreachable")

    override fun transform(expr: UInputMapLengthReading<TvmType, TvmSizeSort>): UExpr<TvmSizeSort> =
        error("unreachable")

    override fun <ElemSort : USort, Reg : Region<Reg>> transform(
        expr: UInputSetReading<TvmType, ElemSort, Reg>,
    ): UBoolExpr = error("unreachable")

    override fun transform(expr: UAllocatedRefSetWithInputElementsReading<TvmType>): UBoolExpr = error("unreachable")

    override fun transform(expr: UInputRefSetWithAllocatedElementsReading<TvmType>): UBoolExpr = error("unreachable")

    override fun transform(expr: UInputRefSetWithInputElementsReading<TvmType>): UBoolExpr = error("unreachable")

    override fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): UExpr<Sort> =
        error("unreachable")

    override fun transform(expr: UIsSubtypeExpr<TvmType>): UBoolExpr = error("unreachable")

    override fun transform(expr: UIsSupertypeExpr<TvmType>): UBoolExpr = error("unreachable")

    override fun transform(expr: UNullRef): UExpr<UAddressSort> = error("unreachable")
}
