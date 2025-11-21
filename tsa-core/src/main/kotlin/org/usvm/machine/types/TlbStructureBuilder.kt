package org.usvm.machine.types

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.ton.TlbCompositeLabel
import org.ton.TlbLabel
import org.ton.TlbStructure
import org.ton.TlbStructureIdProvider
import org.usvm.UConcreteHeapRef
import org.usvm.api.writeField
import org.usvm.machine.state.TvmState
import org.usvm.machine.types.memory.SwitchField

private sealed interface LabelBuilder

private class KnownTypePrefixBuilder(
    val label: TlbLabel,
    val initializeTlbField: (TvmState, UConcreteHeapRef, Int) -> Unit,
) : LabelBuilder

private class ConstantFieldBuilder(
    val bitString: String,
) : LabelBuilder

class TlbStructureBuilder private constructor(
    private val labelBuilders: PersistentList<LabelBuilder>,
) {
    companion object {
        fun new() = TlbStructureBuilder(persistentListOf())

        val empty = new()
    }

    fun end(
        owner: TlbCompositeLabel,
        state: TvmState,
        address: UConcreteHeapRef,
    ): TlbStructure =
        labelBuilders.foldRight(TlbStructure.Empty as TlbStructure) { builder, suffix ->
            val id = TlbStructureIdProvider.provideId()
            when (builder) {
                is ConstantFieldBuilder -> {
                    val ctx = state.ctx
                    val switchField = SwitchField(id, persistentListOf(), listOf(suffix.id))
                    val sort = switchField.getSort(ctx)
                    state.memory.writeField(address, switchField, sort, ctx.mkBv(0, sort), guard = ctx.trueExpr)
                    TlbStructure.SwitchPrefix(
                        id = id,
                        switchSize = builder.bitString.length,
                        givenVariants = mapOf(builder.bitString to suffix),
                        owner = owner,
                    )
                }

                is KnownTypePrefixBuilder -> {
                    builder.initializeTlbField(state, address, id)
                    TlbStructure.KnownTypePrefix(
                        id = id,
                        typeLabel = builder.label,
                        typeArgIds = emptyList(),
                        rest = suffix,
                        owner = owner,
                    )
                }
            }
        }

    fun addTlbLabel(
        label: TlbLabel,
        initializeTlbField: (TvmState, UConcreteHeapRef, Int) -> Unit,
    ): TlbStructureBuilder {
        // [label] must be deduced from store operations, and such labels have zero arity.
        // So, there is no need to support type arguments here.
        check(label.arity == 0) {
            "Only labels without arguments can be used in builder structures, but label $label has arity ${label.arity}"
        }
        return TlbStructureBuilder(labelBuilders.add(KnownTypePrefixBuilder(label, initializeTlbField)))
    }

    fun addConstant(bitString: String): TlbStructureBuilder {
        when (val last = labelBuilders.lastOrNull()) {
            is ConstantFieldBuilder -> {
                val updatedLast = ConstantFieldBuilder(last.bitString + bitString)
                return TlbStructureBuilder(labelBuilders.set(labelBuilders.lastIndex, updatedLast))
            }

            else -> TlbStructureBuilder(labelBuilders.add(ConstantFieldBuilder(bitString)))
        }
        return TlbStructureBuilder(labelBuilders.add(ConstantFieldBuilder(bitString)))
    }
}
