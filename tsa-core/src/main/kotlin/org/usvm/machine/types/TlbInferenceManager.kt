package org.usvm.machine.types

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.ton.TlbStructure
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef

class TlbInferenceManager(
    private var structureInsteadOfUnknown: PersistentMap<Pair<UConcreteHeapAddress, List<Int>>, TlbStructure> =
        persistentMapOf(),
    private var fixedRefs: PersistentSet<UConcreteHeapAddress> = persistentSetOf(),
) {
    fun clone() = TlbInferenceManager(structureInsteadOfUnknown, fixedRefs)

    fun fixateRef(ref: UConcreteHeapRef) {
        fixedRefs = fixedRefs.add(ref.address)
    }

    fun getInferredStruct(
        ref: UConcreteHeapRef,
        path: List<Int>,
    ) = structureInsteadOfUnknown[ref.address to path]

    fun isFixated(ref: UConcreteHeapRef): Boolean = ref.address in fixedRefs

    fun addInferredStruct(
        ref: UConcreteHeapRef,
        path: List<Int>,
        struct: TlbStructure,
    ) {
        check(!isFixated(ref)) {
            "Cannot infer structure for fixated ref"
        }
        structureInsteadOfUnknown = structureInsteadOfUnknown.put(ref.address to path, struct)
    }
}
