package org.usvm.machine.fields

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
import org.usvm.utils.extractAddresses
import org.usvm.utils.intValueOrNull

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

    fun readCellDataLength(
        state: TvmState,
        cellRef: UHeapRef,
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
                }.zeroExtendToSort(sizeSort)
        }

    fun getUpperBound(
        ctx: TvmContext,
        cellRef: UHeapRef,
    ): Int? {
        val refs = ctx.extractAddresses(cellRef, extractStatic = true, extractAllocated = true)
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
        if (upperBound != null) {
            builderLengthUpperBoundTracker = builderLengthUpperBoundTracker.setUpperBound(cellRef, upperBound)
        }
        val valueToWrite = value.extractToSort(cellDataLengthSort)
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
        val fromRefs = extractAddresses(from, extractAllocated = true, extractStatic = true).map { it.second }
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
        private val BITS_FOR_FIELD = 10u
    }
}
