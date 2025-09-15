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
import org.usvm.machine.types.TvmType
import org.usvm.memory.UWritableMemory
import org.usvm.sizeSort
import org.usvm.utils.extractAddresses
import org.usvm.utils.intValueOrNull

class TvmCellDataLengthFieldManager(
    private var builderLengthUpperBoundTracker: TvmBuilderLengthUpperBoundTracker =
        TvmBuilderLengthUpperBoundTracker(
            persistentMapOf(),
        ),
) {
    fun clone() = TvmCellDataLengthFieldManager(builderLengthUpperBoundTracker)

    fun readCellDataLength(
        state: TvmState,
        cellRef: UHeapRef,
    ): UExpr<TvmSizeSort> =
        with(state.ctx) {
            return state.memory.readField(cellRef, cellDataLengthField, sizeSort).also {
                val bound = getUpperBound(state.ctx, cellRef)
                val length = it.intValueOrNull
                if (bound != null && length != null) {
                    check(length <= bound) {
                        "Unexpected upper bound for $cellRef. Bound: $bound, length: $length."
                    }
                }
            }
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
        memory.writeField(cellRef, cellDataLengthField, sizeSort, value, guard = trueExpr)
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
    }
}
