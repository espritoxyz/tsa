package org.usvm.machine.fields

import io.ksmt.expr.KBvExtractExpr
import kotlinx.collections.immutable.persistentMapOf
import org.ton.bytecode.TvmField
import org.ton.bytecode.TvmFieldImpl
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.state.TvmState
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmSliceType
import org.usvm.machine.types.TvmType
import org.usvm.memory.UWritableMemory
import org.usvm.sizeSort
import org.usvm.utils.flattenReferenceIte
import org.usvm.utils.intValueOrNull
import org.usvm.utils.splitAndRead

class TvmCellDataLengthFieldManager(
    private val ctx: TvmContext,
    private var builderLengthUpperBoundTracker: TvmBuilderLengthUpperBoundTracker =
        TvmBuilderLengthUpperBoundTracker(
            persistentMapOf(),
        ),
) {
    private val cellDataLengthSort = ctx.mkBvSort(BITS_FOR_FIELD)

    fun clone() = TvmCellDataLengthFieldManager(ctx, builderLengthUpperBoundTracker)

    fun readSliceDataPos(
        state: TvmState,
        sliceRef: UHeapRef,
    ): UExpr<TvmSizeSort> =
        with(ctx) {
            state.memory.readField(sliceRef, sliceDataPosField, sizeSort)
        }

    fun writeSliceDataPos(
        state: TvmState,
        sliceRef: UHeapRef,
        value: UExpr<TvmSizeSort>,
    ) {
        writeSliceDataPos(state.memory, sliceRef, value)
    }

    fun writeSliceDataPos(
        memory: UWritableMemory<TvmType>,
        sliceRef: UHeapRef,
        value: UExpr<TvmSizeSort>,
    ) = with(ctx) {
        memory.writeField(sliceRef, sliceDataPosField, sizeSort, value, guard = trueExpr)
    }

    private fun readConcreteCellDataLength(
        state: TvmState,
        cellRef: UConcreteHeapRef,
    ): UExpr<TvmSizeSort> =
        with(ctx) {
            return state.memory
                .readField(cellRef, cellDataLengthField, cellDataLengthSort)
                .also {
                    val bound = getUpperBound(state.ctx, cellRef)
                    val length = it.intValueOrNull
                    if (bound != null && length != null) {
                        check(length <= bound) {
                            "Unexpected upper bound for $cellRef. Bound: $bound, length: $length."
                        }
                    }
                }.let {
                    // hack: value in this field is either input symbol or
                    // some value that was written with [writeCellDataLength].
                    // If this is the last case, we can get rid of the [extract] that was added in [writeCellDataLength].
                    if (it is KBvExtractExpr && it.value.sort == sizeSort) {
                        @Suppress("unchecked_cast")
                        it.value as UExpr<TvmSizeSort>
                    } else {
                        it.zeroExtendToSort(sizeSort)
                    }
                }
        }

    fun readCellDataLength(
        state: TvmState,
        cellRef: UHeapRef,
    ): UExpr<TvmSizeSort> =
        with(ctx) {
            return splitAndRead(cellRef) { concreteRef ->
                readConcreteCellDataLength(state, concreteRef)
            }
        }

    fun getUpperBound(
        ctx: TvmContext,
        cellRef: UHeapRef,
    ): Int? {
        val refs = ctx.flattenReferenceIte(cellRef, extractStatic = true, extractAllocated = true)
        return refs.maxOf {
            builderLengthUpperBoundTracker.builderRefToLengthUpperBound[it.second]
                ?: return null
        }
    }

    fun writeCellDataLength(
        ctx: TvmContext,
        memory: UWritableMemory<TvmType>,
        cellRef: UConcreteHeapRef,
        value: UExpr<TvmSizeSort>,
        upperBound: Int?,
    ) = with(ctx) {
        builderLengthUpperBoundTracker = builderLengthUpperBoundTracker.setUpperBound(cellRef, upperBound)
        val valueToWrite = mkBvExtractExprNoSimplify(high = BITS_FOR_FIELD.toInt() - 1, low = 0, value)
        memory.writeField(cellRef, cellDataLengthField, cellDataLengthSort, valueToWrite, guard = trueExpr)
    }

    fun writeCellDataLength(
        state: TvmState,
        cellRef: UConcreteHeapRef,
        value: UExpr<TvmSizeSort>,
        upperBound: Int?,
    ) {
        writeCellDataLength(state.ctx, state.memory, cellRef, value, upperBound)
    }

    fun copyLength(
        state: TvmState,
        from: UHeapRef,
        to: UConcreteHeapRef,
    ) = with(state.ctx) {
        val value = readCellDataLength(state, from)
        val fromRefs = flattenReferenceIte(from, extractAllocated = true, extractStatic = true).map { it.second }
        val upperBound =
            run {
                fromRefs.maxOf {
                    builderLengthUpperBoundTracker.builderRefToLengthUpperBound[it] ?: return@run null
                }
            }
        writeCellDataLength(state, to, value, upperBound)
    }

    companion object {
        private val cellDataLengthField: TvmField = TvmFieldImpl(TvmCellType, "dataLength")
        private val sliceDataPosField: TvmField = TvmFieldImpl(TvmSliceType, "dataPos")
        private const val BITS_FOR_FIELD = 10u
    }
}
