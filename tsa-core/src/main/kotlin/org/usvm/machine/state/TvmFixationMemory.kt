package org.usvm.machine.state

import io.ksmt.expr.KInterpretedValue
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIndexedMocker
import org.usvm.UMockEvaluator
import org.usvm.USort
import org.usvm.collection.set.primitive.UAllocatedSet
import org.usvm.collection.set.primitive.UAllocatedSetId
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.collection.set.primitive.USetRegionId
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UTypeEvaluator
import org.usvm.machine.TvmContext
import org.usvm.machine.types.TvmType
import org.usvm.memory.ULValue
import org.usvm.memory.UMemory
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.UReadOnlyRegistersStack
import org.usvm.memory.URegistersStack
import org.usvm.memory.UWritableMemory
import org.usvm.regions.SetRegion

class TvmFixationMemory(
    ctx: TvmContext,
    values: TvmFixationMemoryValues,
) : UWritableMemory<TvmType> {
    private val nullRef = values.nullRef

    private val regions: Map<UMemoryRegionId<*, *>, UReadOnlyMemoryRegion<*, *>>

    override fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UReadOnlyMemoryRegion<Key, Sort> {
        @Suppress("unchecked_cast")
        return (regions[regionId] as UReadOnlyMemoryRegion<Key, Sort>?) ?: DefaultRegion(regionId)
    }

    override val mocker: UMockEvaluator
        get() = UIndexedMocker<Nothing>()

    override val stack: UReadOnlyRegistersStack
        get() = URegistersStack()

    override fun nullRef(): UHeapRef = nullRef

    override val ownership: MutabilityOwnership = MutabilityOwnership()

    override fun toWritableMemory(ownership: MutabilityOwnership) = this

    override fun <Key, Sort : USort> setRegion(
        regionId: UMemoryRegionId<Key, Sort>,
        newRegion: UMemoryRegion<Key, Sort>
    ) {
        error("Illegal operation for TvmFixationMemory")
    }

    override fun <Key, Sort : USort> write(lvalue: ULValue<Key, Sort>, rvalue: UExpr<Sort>, guard: UBoolExpr) {
        error("Illegal operation for TvmFixationMemory")
    }

    override fun allocConcrete(type: TvmType): UConcreteHeapRef {
        error("Illegal operation for TvmFixationMemory")
    }

    override fun allocStatic(type: TvmType): UConcreteHeapRef {
        error("Illegal operation for TvmFixationMemory")
    }

    override val types: UTypeEvaluator<TvmType>
        get() = error("types shouldn't be requested from TvmFixationMemory")

    private class DefaultRegion<Key, Sort : USort>(
        private val regionId: UMemoryRegionId<Key, Sort>,
    ) : UReadOnlyMemoryRegion<Key, Sort> {
        override fun read(key: Key): UExpr<Sort> = regionId.emptyRegion().read(key)
    }

    class TvmFixationMemoryValues(
        val sets: Set<ConcreteSet>,
        val nullRef: UHeapRef,
    ) {
        fun union(other: TvmFixationMemoryValues): TvmFixationMemoryValues {
            check(((sets.map { it.ref }) intersect (other.sets.map { it.ref }).toSet()).isEmpty()) {
                "Unexpected intersection of concrete sets"
            }

            check(nullRef == other.nullRef) {
                "Unexpected difference in nullRef"
            }

            return TvmFixationMemoryValues(sets union other.sets, nullRef)
        }

        companion object {
            fun empty(memory: UMemory<*, *>) =
                TvmFixationMemoryValues(emptySet(), nullRef = memory.nullRef())
        }
    }

    class ConcreteSet(
        val ref: UConcreteHeapRef,
        val dictId: DictId,
        val elements: Set<KInterpretedValue<UBvSort>>,
    )

    private class FixedSetRegion<ElementSort : USort>(
        val sets: Map<UConcreteHeapRef, UAllocatedSet<DictId, ElementSort, SetRegion<UExpr<UBvSort>>>>
    ) : UReadOnlyMemoryRegion<USetEntryLValue<DictId, ElementSort, SetRegion<UExpr<UBvSort>>>, UBoolSort> {
        override fun read(key: USetEntryLValue<DictId, ElementSort, SetRegion<UExpr<UBvSort>>>): UExpr<UBoolSort> {
            check(key.setRef is UConcreteHeapRef) {
                "Unexpected key: ${key.setRef}"
            }

            val collection = sets[key.setRef]
                ?: return key.memoryRegionId.emptyRegion().read(key)

            return collection.read(key.setElement)
        }
    }

    init {
        val fixatedRegions = mutableMapOf<UMemoryRegionId<*, *>, UReadOnlyMemoryRegion<*, *>>()

        values.sets.groupBy { it.dictId }.forEach { (dictId, sets) ->
            val sort = ctx.mkBvSort(dictId.keyLength.toUInt())
            val regionId = USetRegionId(sort, dictId, DictKeyInfo)

            val usvmSets = mutableMapOf<UConcreteHeapRef, UAllocatedSet<DictId, UBvSort, SetRegion<UExpr<UBvSort>>>>()
            sets.forEach { set ->
                val setId = UAllocatedSetId(set.ref.address, sort, dictId, DictKeyInfo)
                val newCollection = setId.initializedSet(set.elements, guard = ctx.trueExpr)
                usvmSets[set.ref] = newCollection
            }

            fixatedRegions[regionId] = FixedSetRegion(usvmSets)
        }

        regions = fixatedRegions
    }
}
