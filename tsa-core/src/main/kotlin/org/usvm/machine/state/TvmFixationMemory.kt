package org.usvm.machine.state

import io.ksmt.expr.KInterpretedValue
import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap
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
import org.usvm.memory.ULValue
import org.usvm.memory.UMemory
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.URegisterStackId
import org.usvm.memory.URegistersStack
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UWritableMemory
import org.usvm.model.UModelBase
import org.usvm.regions.Region
import org.usvm.regions.SetRegion
import org.usvm.utils.extractAddresses

class TvmPartialModel(
    private val ctx: TvmContext,
    private val model: UModelBase<TvmType>,
//    override val composer: UComposer<TvmType, TvmSizeSort> = ,
//    private val values: TvmFixationMemoryValues,
//    private val typeConstraints: UTypeConstraints<TvmType>,
    regions: PersistentMap<UMemoryRegionId<*, *>, UReadOnlyMemoryRegion<*, *>>,
    ownership: MutabilityOwnership = model.ownership
) : UModelBase<TvmType>(ctx, model.stack, model.types, model.mocker, regions, model.nullRef, ownership) {
    private var mutableRegions: PersistentMap<UMemoryRegionId<*, *>, UReadOnlyMemoryRegion<*, *>> = regions



//    constructor(
//        ctx: TvmContext,
//        values: TvmFixationMemoryValues,
////        typeConstraints: UTypeConstraints<TvmType>,
//        model: UModelBase<TvmType>,
//    ) : this(ctx, model, /*values, typeConstraints,*/ patchedRegions(model, values))

    override fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UReadOnlyMemoryRegion<Key, Sort> {
        if (regionId is URegisterStackId) {
            return stack.uncheckedCast()
        }

        return mutableRegions[regionId]?.uncheckedCast()
            ?: error("Partial model has no region: $regionId")
    }

    override fun toWritableMemory(ownership: MutabilityOwnership): UWritableMemory<TvmType> =
        TvmPartialModel(ctx, model, /*values, typeConstraints,*/ mutableRegions, ownership)

    override fun <Key, Sort : USort> write(lvalue: ULValue<Key, Sort>, rvalue: UExpr<Sort>, guard: UBoolExpr) {
        val regionId = lvalue.memoryRegionId
        val region = getRegion(regionId)
        if (region !is UMemoryRegion<Key, Sort>) {
            error("Cannot write to region ${lvalue.memoryRegionId}")
        }
        val newRegion = region.write(lvalue.key, rvalue, guard, ownership)
        mutableRegions = mutableRegions.put(regionId, newRegion)
    }

//    override val composer = TvmFixationComposer(ctx, values, typeConstraints, this, ownership)

}

//class TvmFixationComposer(
//    ctx: TvmContext,
//    values: TvmFixationMemoryValues,
//    typeConstraints: UTypeConstraints<TvmType>,
//    private val model: UModelBase<TvmType>,
//    ownership: MutabilityOwnership,
//) : UComposer<TvmType, TvmSizeSort>(
//    ctx,
//    UMemory(ctx, ownership, typeConstraints, URegistersStack(), UIndexedMocker<Nothing>(), persistentHashMapOf()),
//    ownership
//) {
//
//    @Suppress("UNCHECKED_CAST")
////    private val writableMemory = this.memory as UMemory<TvmType, TvmSizeSort>
////    private var regions: PersistentMap<UMemoryRegionId<*, *>, UMemoryRegion<*, *>> = persistentMapOf()
//    private val setsToConcretize = values.sets.map { it.ref to it.dictId }
//
////    init {
////        values.sets.groupBy { it.dictId }.forEach { (dictId, sets) ->
////            val sort = ctx.mkBvSort(dictId.keyLength.toUInt())
////            val regionId = USetRegionId(sort, dictId, DictKeyInfo)
////
////            val usvmSets = mutableMapOf<UConcreteHeapRef, UAllocatedSet<DictId, UBvSort, SetRegion<UExpr<UBvSort>>>>()
////            sets.forEach { set ->
////                val setId = UAllocatedSetId(set.ref.address, sort, dictId, DictKeyInfo)
////                val newCollection = setId.initializedSet(set.elements, guard = ctx.trueExpr)
////                usvmSets[set.ref] = newCollection
////            }
////
////            writableMemory.setRegion(regionId, FixedSetRegion(ctx, sort, dictId, DictKeyInfo, usvmSets, regionId.emptyRegion()))
////            regions = regions.put(regionId, FixedSetRegion(ctx, sort, dictId, DictKeyInfo, usvmSets, model.getRegion(regionId)))
////        }
////    }
//
//    override fun <ElemSort : USort, Reg : Region<Reg>> transform(
//        expr: UInputSetReading<TvmType, ElemSort, Reg>
//    ): UBoolExpr = with(expr) {
//        if (expr.address to DictId((expr.collection.collectionId.elementSort as UBvSort).sizeBits.toInt()) !in setsToConcretize) {
//            return super.transform(expr)
//        }
//        val mappedKey = collection.collectionId.keyInfo().mapKey(address to element, this@TvmFixationComposer)
////        val patchedRegions = model.regions + regions
////        val patchedModel = UModelBase(
////            ctx.tctx(),
////            model.stack,
////            model.types,
////            model.mocker,
////            patchedRegions,
////            model.nullRef,
////            model.ownership
////        )
////        val patchedMemory = memory.toWritableMemory(ownership)
////        for (entry in model.regions) {
////            patchedMemory.setRegion(entry.key, entry.value as)
////        }
////        for (entry in regions) {
////            patchedMemory.setRegion(entry.key, entry.value as)
////        }
//        val result = collection.read(mappedKey, UComposer(ctx.tctx(), patchedMemory, ownership))
//        return result
//    }
//
//}

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
    private var partialModel: TvmPartialModel
    @Suppress("UNCHECKED_CAST")
    private val writableMemory = this.memory as UMemory<TvmType, TvmSizeSort>

    init {
        var regions = model.regions.toPersistentMap()
        values.sets.groupBy { it.dictId }.forEach { (dictId, sets) ->
            val sort = ctx.mkBvSort(dictId.keyLength.toUInt())
            val regionId = USetRegionId(sort, dictId, DictKeyInfo)

            val usvmSets = mutableMapOf<UConcreteHeapRef, UAllocatedSet<DictId, UBvSort, SetRegion<UExpr<UBvSort>>>>()
            sets.forEach { set ->
                val setId = UAllocatedSetId(set.ref.address, sort, dictId, DictKeyInfo)
                val newCollection = setId.initializedSet(set.elements, guard = ctx.trueExpr)
                usvmSets[set.ref] = newCollection
            }

            val emptyRegion = regionId.emptyRegion()
//            val partialModelRegion = FixedSetRegion(ctx, sort, dictId, DictKeyInfo, usvmSets, model.getRegion(regionId), emptyRegion)
            writableMemory.setRegion(regionId, FixedSetRegion(ctx, sort, dictId, DictKeyInfo, usvmSets, emptyRegion, emptyRegion))
            regions = regions.put(regionId, FixedSetRegion(ctx, sort, dictId, DictKeyInfo, usvmSets, model.getRegion(regionId), emptyRegion))
        }
        partialModel = TvmPartialModel(ctx, model, regions, ownership)
    }


//    @Suppress("UNCHECKED_CAST")
//    private val writableMemory = this.memory as UMemory<TvmType, TvmSizeSort>
//    private var regions: PersistentMap<UMemoryRegionId<*, *>, UMemoryRegion<*, *>> = persistentMapOf()
    private val setsToConcretize = values.sets.map { it.ref to it.dictId }

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
//        val patchedMemory = memory.toWritableMemory(ownership)
//        for (entry in model.regions) {
//            patchedMemory.setRegion(entry.key, entry.value as)
//        }
//        for (entry in regions) {
//            patchedMemory.setRegion(entry.key, entry.value as)
//        }
        val result = collection.read(mappedKey, UComposer(ctx.tctx(), partialModel, ownership))
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
    val readRegion: UReadOnlyMemoryRegion<USetEntryLValue<DictId, ElementSort, SetRegion<UExpr<UBvSort>>>, UBoolSort>,
    val writeRegion: USetRegion<DictId, ElementSort, SetRegion<UExpr<UBvSort>>>,
) : USetRegion<DictId, ElementSort, SetRegion<UExpr<UBvSort>>> {
    override fun read(key: USetEntryLValue<DictId, ElementSort, SetRegion<UExpr<UBvSort>>>): UExpr<UBoolSort> {
        check(key.setRef is UConcreteHeapRef) {
            "Unexpected key: ${key.setRef}"
        }

        val collection =
            sets[key.setRef]
                ?: return readRegion.read(key) //key.memoryRegionId.emptyRegion().read(key)

        return collection.read(key.setElement)
    }

    override fun allocatedSetElements(address: UConcreteHeapAddress): UAllocatedSet<DictId, ElementSort, SetRegion<UExpr<UBvSort>>> =
        writeRegion.allocatedSetElements(address)

    override fun inputSetElements(): UInputSet<DictId, ElementSort, SetRegion<UExpr<UBvSort>>> =
        writeRegion.inputSetElements()

    override fun initializeAllocatedSet(
        address: UConcreteHeapAddress,
        setType: DictId,
        sort: ElementSort,
        content: Set<UExpr<ElementSort>>,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
        makeDisjointCheck: Boolean
    ): USetRegion<DictId, ElementSort, SetRegion<UExpr<UBvSort>>> =
        writeRegion.initializeAllocatedSet(address, setType, sort, content, operationGuard, ownership, makeDisjointCheck)

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
    ): USetRegion<DictId, ElementSort, SetRegion<UExpr<UBvSort>>> =
        writeRegion.union(srcRef, dstRef, operationGuard, ownership)

    override fun write(
        key: USetEntryLValue<DictId, ElementSort, SetRegion<UExpr<UBvSort>>>,
        value: UExpr<UBoolSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership
    ): UMemoryRegion<USetEntryLValue<DictId, ElementSort, SetRegion<UExpr<UBvSort>>>, UBoolSort> =
        writeRegion.write(key, value, guard, ownership)
}
//}
