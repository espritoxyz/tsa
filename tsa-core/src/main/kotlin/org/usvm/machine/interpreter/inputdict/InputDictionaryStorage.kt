package org.usvm.machine.interpreter.inputdict

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef

/**
 * The input dictionary reference structure is as follows:
 * - there exist some root information units that store the base of an input dictionary
 * - [InputDict]s reference root information units
 * - many [InputDict]s might reference a single root information unit (for instance,
 * when many dictionaries we created on the base of the same root input dictionary).
 * - most of the queries mutate the state of the root information unit
 *
 * To correctly handle the change of the root information unit, a layer of indirection was introduced:
 * each root information unit has an id (local to the [org.usvm.machine.state.TvmState]) that is used
 * by [InputDict]s to reference it.
 * Such an architecture allows us to implement the procedure as shown in [set].
 */
class InputDictionaryStorage(
    val memory: PersistentMap<UConcreteHeapRef, InputDict> = persistentMapOf(),
    val rootInformation: PersistentMap<Int, InputDictRootInformation> = persistentMapOf(),
) {
    /**
     * DEPRECATED
     *
     * In the better scenario, you should independently create new input dictionary entries and update the root data.
     * Use [createDictEntry] and [updateRootInputDictionary] instead
     * @param newInputDictRootInformation is null iff you don't want to update the root dictionary
     */
    fun set(
        dictConcreteRef: UConcreteHeapRef,
        inputDict: InputDict,
        newInputDictRootInformation: InputDictRootInformation? = null,
    ): InputDictionaryStorage =
        InputDictionaryStorage(
            memory.put(dictConcreteRef, inputDict),
            if (newInputDictRootInformation != null) {
                rootInformation.put(inputDict.rootInputDictId, newInputDictRootInformation)
            } else {
                rootInformation
            },
        )

    fun hasInputDictEntryAtRef(ref: UHeapRef?) = memory.containsKey(ref)

    fun getRootInfoByIdOrThrow(id: Int) =
        rootInformation[id]
            ?: error("No input dictionary root info stored at id $id")

    fun createDictEntry(
        ref: UConcreteHeapRef,
        inputDict: InputDict,
    ): InputDictionaryStorage {
        assert(ref !in memory) { "InputDict object is deeply immutable and must be written only once" }
        return InputDictionaryStorage(memory = memory.put(ref, inputDict), rootInformation = rootInformation)
    }

    fun updateRootInputDictionary(
        id: Int,
        rootInputDict: InputDictRootInformation,
    ) = InputDictionaryStorage(
        memory = memory,
        rootInformation = rootInformation.put(id, rootInputDict),
    )
}
