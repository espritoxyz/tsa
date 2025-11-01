package org.usvm.machine.interpreter.inputdict

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UConcreteHeapRef

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
    val inputDicts: PersistentMap<Int, InputDictRootInformation> = persistentMapOf(),
) {
    /**
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
                inputDicts.put(inputDict.rootInputDictId, newInputDictRootInformation)
            } else {
                inputDicts
            },
        )
}
