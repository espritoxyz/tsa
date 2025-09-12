package org.usvm.machine.state

import io.ksmt.expr.KInterpretedValue
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UBvSort
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIndexedMocker
import org.usvm.USort
import org.usvm.collection.set.USymbolicSetElementsCollector
import org.usvm.collection.set.primitive.UAllocatedSet
import org.usvm.collection.set.primitive.UAllocatedSetId
import org.usvm.collection.set.primitive.UInputSet
import org.usvm.collection.set.primitive.UInputSetReading
import org.usvm.collection.set.primitive.UPrimitiveSetEntries
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.collection.set.primitive.USetRegion
import org.usvm.collection.set.primitive.USetRegionId
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.constraints.UTypeConstraints
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.types.TvmType
import org.usvm.memory.UMemory
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.URegistersStack
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.model.UModelBase
import org.usvm.regions.Region
import org.usvm.regions.SetRegion
import org.usvm.utils.extractAddresses

class TvmFixationComposer(
    ctx: TvmContext,
    values: TvmFixationMemoryValues,
    typeConstraints: UTypeConstraints<TvmType>,
    private val model: UModelBase<TvmType>,
    ownership: MutabilityOwnership,
) : UComposer<TvmType, TvmSizeSort>(
    ctx,
    UMemory(ctx, ownership, typeConstraints, URegistersStack(), UIndexedMocker<Nothing>(), persistentHashMapOf()),
    ownership
) {

    @Suppress("UNCHECKED_CAST")
    private val writableMemory = this.memory as UMemory<TvmType, TvmSizeSort>
    private var regions: PersistentMap<UMemoryRegionId<*, *>, UMemoryRegion<*, *>> = persistentMapOf()
    private val setsToConcretize = values.sets.map { it.ref to it.dictId }

    init {
        values.sets.groupBy { it.dictId }.forEach { (dictId, sets) ->
            val sort = ctx.mkBvSort(dictId.keyLength.toUInt())
            val regionId = USetRegionId(sort, dictId, DictKeyInfo)

            val usvmSets = mutableMapOf<UConcreteHeapRef, UAllocatedSet<DictId, UBvSort, SetRegion<UExpr<UBvSort>>>>()
            sets.forEach { set ->
                val setId = UAllocatedSetId(set.ref.address, sort, dictId, DictKeyInfo)
                val newCollection = setId.initializedSet(set.elements, guard = ctx.trueExpr)
                usvmSets[set.ref] = newCollection
            }

            writableMemory.setRegion(regionId, FixedSetRegion(ctx, sort, dictId, DictKeyInfo, usvmSets, regionId.emptyRegion()))
            regions = regions.put(regionId, FixedSetRegion(ctx, sort, dictId, DictKeyInfo, usvmSets, model.getRegion(regionId)))
        }
    }

    override fun <ElemSort : USort, Reg : Region<Reg>> transform(
        expr: UInputSetReading<TvmType, ElemSort, Reg>
    ): UBoolExpr = with(expr) {
        if (expr.address to DictId((expr.collection.collectionId.elementSort as UBvSort).sizeBits.toInt()) !in setsToConcretize) {
            return super.transform(expr)
        }
        val mappedKey = collection.collectionId.keyInfo().mapKey(address to element, this@TvmFixationComposer)
//        val patchedRegions = model.regions + regions
//        val patchedModel = UModelBase(
//            ctx.tctx(),
//            model.stack,
//            model.types,
//            model.mocker,
//            patchedRegions,
//            model.nullRef,
//            model.ownership
//        )
        val patchedMemory = memory.toWritableMemory(ownership)
        for (entry in model.regions) {
            patchedMemory.setRegion(entry.key, entry.value as)
        }
        for (entry in regions) {
            patchedMemory.setRegion(entry.key, entry.value as )
        }
        val result = collection.read(mappedKey, UComposer(ctx.tctx(), patchedMemory, ownership))
        return result
    }

}

//class TvmFixationMemory(
//    private val ctx: TvmContext,
//    val values: TvmFixationMemoryValues,
//    val model: UModelBase<TvmType>
//) : UWritableMemory<TvmType> {
//    private val nullRef = values.nullRef
//
//    val regions: Map<UMemoryRegionId<*, *>, UReadOnlyMemoryRegion<*, *>>
//
//    override fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UReadOnlyMemoryRegion<Key, Sort> {
//        @Suppress("unchecked_cast")
//        return /*(regions[regionId] as UReadOnlyMemoryRegion<Key, Sort>?) ?: */DefaultRegion(regionId)
//    }
//
//    override val mocker: UMockEvaluator
//        get() = UIndexedMocker<Nothing>()
//
//    override val stack: UReadOnlyRegistersStack
//        get() = URegistersStack()
//
//    override fun nullRef(): UHeapRef = nullRef
//
//    override val ownership: MutabilityOwnership = MutabilityOwnership()
//
//    override fun toWritableMemory(ownership: MutabilityOwnership) =
//        TvmFixationMemory(ctx, values, model)
//
//    override fun <Key, Sort : USort> setRegion(
//        regionId: UMemoryRegionId<Key, Sort>,
//        newRegion: UMemoryRegion<Key, Sort>,
//    ) {
//        error("Illegal operation for TvmFixationMemory")
//    }
//
//    override fun <Key, Sort : USort> write(
//        lvalue: ULValue<Key, Sort>,
//        rvalue: UExpr<Sort>,
//        guard: UBoolExpr,
//    ) {
//        error("Illegal operation for TvmFixationMemory")
//    }
//
//    override fun allocConcrete(type: TvmType): UConcreteHeapRef {
//        error("Illegal operation for TvmFixationMemory")
//    }
//
//    override fun allocStatic(type: TvmType): UConcreteHeapRef {
//        error("Illegal operation for TvmFixationMemory")
//    }
//
//    override val types: UTypeEvaluator<TvmType>
//        get() = error("Types shouldn't be requested from TvmFixationMemory")
//
//    private class DefaultRegion<Key, Sort : USort>(
//        private val regionId: UMemoryRegionId<Key, Sort>,
//    ) : UReadOnlyMemoryRegion<Key, Sort> {
//        override fun read(key: Key): UExpr<Sort> = regionId.emptyRegion().read(key)
//    }

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

    fun isEmpty(): Boolean = sets.isEmpty()

    companion object {
        fun empty(nullRef: UHeapRef) = TvmFixationMemoryValues(emptySet(), nullRef = nullRef)
    }
}

class ConcreteSet(
    val ref: UConcreteHeapRef,
    val dictId: DictId,
    val elements: Set<KInterpretedValue<UBvSort>>,
)

private class FixedSetRegion<ElementSort : USort>(
    val ctx: TvmContext,
    val elementSort: ElementSort,
    val dictId: DictId,
    val elementInfo: USymbolicCollectionKeyInfo<UExpr<ElementSort>, SetRegion<UExpr<UBvSort>>>,
    val sets: Map<UConcreteHeapRef, UAllocatedSet<DictId, ElementSort, SetRegion<UExpr<UBvSort>>>>,
    val baseRegion: UReadOnlyMemoryRegion<USetEntryLValue<DictId, ElementSort, SetRegion<UExpr<UBvSort>>>, UBoolSort>
) : USetRegion<DictId, ElementSort, SetRegion<UExpr<UBvSort>>> {
    override fun read(key: USetEntryLValue<DictId, ElementSort, SetRegion<UExpr<UBvSort>>>): UExpr<UBoolSort> {
        check(key.setRef is UConcreteHeapRef) {
            "Unexpected key: ${key.setRef}"
        }

        val collection =
            sets[key.setRef]
                ?: return baseRegion.read(key) //key.memoryRegionId.emptyRegion().read(key)

        return collection.read(key.setElement)
    }

    override fun allocatedSetElements(address: UConcreteHeapAddress): UAllocatedSet<DictId, ElementSort, SetRegion<UExpr<UBvSort>>> {
        TODO("Not yet implemented")
    }

    override fun inputSetElements(): UInputSet<DictId, ElementSort, SetRegion<UExpr<UBvSort>>> {
        TODO("Not yet implemented")
    }

    override fun initializeAllocatedSet(
        address: UConcreteHeapAddress,
        setType: DictId,
        sort: ElementSort,
        content: Set<UExpr<ElementSort>>,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
        makeDisjointCheck: Boolean
    ): USetRegion<DictId, ElementSort, SetRegion<UExpr<UBvSort>>> {
        TODO("Not yet implemented")
    }

    override fun setEntries(ref: UHeapRef): UPrimitiveSetEntries<DictId, ElementSort, SetRegion<UExpr<UBvSort>>> {
        val entries = UPrimitiveSetEntries<DictId, ElementSort, SetRegion<UExpr<UBvSort>>>()

        val refs = ctx.extractAddresses(ref)
        refs.forEach { (_, ref) ->
            val set = sets[ref]
                ?: return@forEach
            val elements = USymbolicSetElementsCollector.collect(set.updates)
            elements.elements.forEach { elem ->
                entries.add(USetEntryLValue(elementSort, ref, elem, dictId, elementInfo))
            }
        }

        return entries
    }

    override fun union(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership
    ): USetRegion<DictId, ElementSort, SetRegion<UExpr<UBvSort>>> {
        TODO("Not yet implemented")
    }

    override fun write(
        key: USetEntryLValue<DictId, ElementSort, SetRegion<UExpr<UBvSort>>>,
        value: UExpr<UBoolSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership
    ): UMemoryRegion<USetEntryLValue<DictId, ElementSort, SetRegion<UExpr<UBvSort>>>, UBoolSort> {
        TODO("Not yet implemented")
    }
}
//}
