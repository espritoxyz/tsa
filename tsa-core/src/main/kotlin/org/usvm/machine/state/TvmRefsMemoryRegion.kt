package org.usvm.machine.state

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.isFalse
import org.usvm.isStaticHeapRef
import org.usvm.machine.map
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.foldHeapRef
import org.usvm.uctx

class TvmRefsMemoryRegion<LValue, KeySort : USort, ValueSort: USort>(
    private var refValue: PersistentMap<UConcreteHeapAddress, TvmRefsRegionUpdateNode<KeySort, ValueSort>> = persistentHashMapOf()
) : UMemoryRegion<LValue, Nothing> {
    override fun read(key: LValue) = error("Use readRefValue")
    override fun write(
        key: LValue,
        value: UExpr<Nothing>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership
    ): UMemoryRegion<LValue, Nothing> = error("Use writeRefValue")

    fun readRefValue(
        ref: UHeapRef,
        key: UExpr<KeySort>,
        valueInfo: TvmRefsRegionValueInfo<ValueSort>,
    ): UExpr<ValueSort> = ref.map(
        concreteMapper = { concreteRef ->
            readRefValue(concreteRef.address, key, readingFromInputRef = false, valueInfo)
        },
        staticMapper = { concreteRef ->
            readRefValue(concreteRef.address, key, readingFromInputRef = true, valueInfo)
        },
        symbolicMapper = { r -> error("Unexpected input ref $r") }
    )

    fun writeRefValue(
        ref: UHeapRef,
        key: UExpr<KeySort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ): TvmRefsMemoryRegion<LValue, KeySort, ValueSort> = foldHeapRef(
        ref = ref,
        initial = this,
        initialGuard = guard,
        staticIsConcrete = true,
        blockOnConcrete = { region, (concreteRef, refGuard) ->
            region.writeRefValue(concreteRef.address, key, value, refGuard)
        },
        blockOnSymbolic = { _, (ref, _) -> error("Unexpected input cell $ref") }
    )

    fun copyRefValues(src: UHeapRef, dst: UConcreteHeapRef): TvmRefsMemoryRegion<LValue, KeySort, ValueSort> =
        foldHeapRef(
            ref = src,
            initial = this,
            initialGuard = src.ctx.trueExpr,
            staticIsConcrete = true,
            blockOnConcrete = { region, (concreteRef, refGuard) ->
                region.copyRefValues(
                    src = concreteRef.address,
                    dst = dst.address,
                    copyFromInputRef = isStaticHeapRef(concreteRef),
                    guard = refGuard
                )
            },
            blockOnSymbolic = { _, (ref, _) -> error("Unexpected input ref $ref") }
        )

    fun getRefsUpdateNode(cell: UConcreteHeapRef): TvmRefsRegionUpdateNode<KeySort, ValueSort>? = refValue[cell.address]

    private fun readRefValue(
        ref: UConcreteHeapAddress,
        key: UExpr<KeySort>,
        readingFromInputRef: Boolean,
        valueInfo: TvmRefsRegionValueInfo<ValueSort>,
    ): UExpr<ValueSort> {
        val initialNode = refValue[ref]

        if (initialNode == null) {
            return readInputRefValue(key, readingFromInputRef, valueInfo) { newNode ->
                refValue = refValue.put(ref, newNode)
            }
        }

        return readRefValue(initialNode, key, readingFromInputRef, valueInfo)
    }

    private fun writeRefValue(
        ref: UConcreteHeapAddress,
        key: UExpr<KeySort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ): TvmRefsMemoryRegion<LValue, KeySort, ValueSort> {
        if (guard.isFalse) return this

        val initialNode = refValue[ref]
        val updatedNode = writeRefValue(initialNode, key, value, guard)
        return TvmRefsMemoryRegion(refValue.put(ref, updatedNode))
    }

    private fun copyRefValues(
        src: UConcreteHeapAddress,
        dst: UConcreteHeapAddress,
        copyFromInputRef: Boolean,
        guard: UBoolExpr
    ): TvmRefsMemoryRegion<LValue, KeySort, ValueSort> {
        if (guard.isFalse || src == dst) return this

        var srcUpdates = refValue[src]
        var updatedRefValue = refValue

        if (srcUpdates == null) {
            srcUpdates = TvmRefsRegionEmptyUpdateNode(prevUpdate = null)
            if (copyFromInputRef) {
                updatedRefValue = updatedRefValue.put(src, srcUpdates)
            }
        }

        val currentDstUpdates = refValue[dst]
        val updateNode = TvmRefsRegionCopyUpdateNode(guard, copyFromInputRef, srcUpdates, currentDstUpdates)
        updatedRefValue = updatedRefValue.put(dst, updateNode)

        return TvmRefsMemoryRegion(updatedRefValue)
    }

    private fun readRefValue(
        node: TvmRefsRegionUpdateNode<KeySort, ValueSort>,
        key: UExpr<KeySort>,
        readingFromInputRef: Boolean,
        valueInfo: TvmRefsRegionValueInfo<ValueSort>,
    ): UExpr<ValueSort> = with(key.uctx) {
        when (node) {
            is TvmRefsRegionPinpointUpdateNode<KeySort, ValueSort> -> {
                val nodeIncludesKey = mkAnd(mkEq(key, node.key), node.guard)
                mkIte(
                    condition = nodeIncludesKey,
                    trueBranch = { node.value },
                    falseBranch = {
                        readPrevRefValue(node, key, readingFromInputRef, valueInfo)
                    }
                )
            }

            is TvmRefsRegionInputNode<KeySort, ValueSort> -> {
                mkIte(
                    condition = mkEq(key, node.key),
                    trueBranch = { valueInfo.actualizeSymbolicValue(node.value) },
                    falseBranch = {
                        readPrevRefValue(node, key, readingFromInputRef, valueInfo)
                    }
                )
            }

            is TvmRefsRegionCopyUpdateNode<KeySort, ValueSort> -> {
                mkIte(
                    condition = node.guard,
                    trueBranch = {
                        readRefValue(node.updates, key, node.copyFromInputRef, valueInfo)
                    },
                    falseBranch = {
                        readPrevRefValue(node, key, readingFromInputRef, valueInfo)
                    }
                )
            }

            is TvmRefsRegionEmptyUpdateNode<KeySort, ValueSort> -> {
                readPrevRefValue(node, key, readingFromInputRef, valueInfo)
            }
        }
    }

    private fun readPrevRefValue(
        node: TvmRefsRegionUpdateNode<KeySort, ValueSort>,
        key: UExpr<KeySort>,
        readingFromInputRef: Boolean,
        valueInfo: TvmRefsRegionValueInfo<ValueSort>,
    ): UExpr<ValueSort> = node.prevUpdate
        ?.let { prev ->
            readRefValue(prev, key, readingFromInputRef, valueInfo)
        }
        ?: readInputRefValue(key, readingFromInputRef, valueInfo) { newPrevNode ->
            node.prevUpdate = newPrevNode
        }

    private inline fun readInputRefValue(
        key: UExpr<KeySort>,
        readingFromInputRef: Boolean,
        valueInfo: TvmRefsRegionValueInfo<ValueSort>,
        updateParentNode: (TvmRefsRegionInputNode<KeySort, ValueSort>) -> Unit
    ): UExpr<ValueSort> = with(key.uctx) {
        if (!readingFromInputRef) {
            // Reading from an allocated ref, no need to provide input value
            return valueInfo.mkDefaultValue()
        }

        val inputValue = valueInfo.mkSymbolicValue()
        val newNode = TvmRefsRegionInputNode(key, inputValue, prevUpdate = null)
        updateParentNode(newNode)

        return inputValue
    }

    private fun writeRefValue(
        node: TvmRefsRegionUpdateNode<KeySort, ValueSort>?,
        key: UExpr<KeySort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ): TvmRefsRegionPinpointUpdateNode<KeySort, ValueSort> = TvmRefsRegionPinpointUpdateNode(key, value, guard, node)

    sealed interface TvmRefsRegionUpdateNode<KeySort : USort, ValueSort: USort> {
        var prevUpdate: TvmRefsRegionUpdateNode<KeySort, ValueSort>?
    }

    data class TvmRefsRegionPinpointUpdateNode<KeySort : USort, ValueSort: USort>(
        val key: UExpr<KeySort>,
        val value: UExpr<ValueSort>,
        val guard: UBoolExpr,
        override var prevUpdate: TvmRefsRegionUpdateNode<KeySort, ValueSort>?
    ) : TvmRefsRegionUpdateNode<KeySort, ValueSort>

    data class TvmRefsRegionInputNode<KeySort : USort, ValueSort: USort>(
        val key: UExpr<KeySort>,
        val value: UExpr<ValueSort>,
        override var prevUpdate: TvmRefsRegionUpdateNode<KeySort, ValueSort>?
    ) : TvmRefsRegionUpdateNode<KeySort, ValueSort>

    data class TvmRefsRegionCopyUpdateNode<KeySort : USort, ValueSort: USort>(
        val guard: UBoolExpr,
        val copyFromInputRef: Boolean,
        val updates: TvmRefsRegionUpdateNode<KeySort, ValueSort>,
        override var prevUpdate: TvmRefsRegionUpdateNode<KeySort, ValueSort>?
    ) : TvmRefsRegionUpdateNode<KeySort, ValueSort>

    data class TvmRefsRegionEmptyUpdateNode<KeySort : USort, ValueSort: USort>(
        override var prevUpdate: TvmRefsRegionUpdateNode<KeySort, ValueSort>?
    ) : TvmRefsRegionUpdateNode<KeySort, ValueSort>

    interface TvmRefsRegionValueInfo<ValueSort : USort> {
        /**
         * Provide a default value. Used in a case of reading from an empty allocated ref.
         * */
        fun mkDefaultValue(): UExpr<ValueSort>

        /**
         * Provide a fresh symbolic value. Use in a case of reading from an empty symbolic ref.
         * Note: symbolic value must be valid (unique) across all states.
         * */
        fun mkSymbolicValue(): UExpr<ValueSort>

        /**
         * Stored symbolic value may be generated in a future states and therefore
         * must be actualized within the current state.
         * */
        fun actualizeSymbolicValue(value: UExpr<ValueSort>): UExpr<ValueSort>
    }
}
