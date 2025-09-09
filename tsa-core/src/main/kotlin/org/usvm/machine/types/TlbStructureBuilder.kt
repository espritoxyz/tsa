package org.usvm.machine.types

import kotlinx.collections.immutable.persistentListOf
import org.ton.TlbCompositeLabel
import org.ton.TlbLabel
import org.ton.TlbStructure
import org.ton.TlbStructureIdProvider
import org.usvm.UConcreteHeapRef
import org.usvm.api.writeField
import org.usvm.machine.TvmContext
import org.usvm.machine.state.TvmState
import org.usvm.machine.types.memory.SwitchField

@JvmInline
value class TlbStructureBuilder(
    val build: (TlbStructure, TlbCompositeLabel, TvmState, UConcreteHeapRef) -> TlbStructure
) {
    fun end(
        owner: TlbCompositeLabel,
        state: TvmState,
        address: UConcreteHeapRef
    ): TlbStructure = build(TlbStructure.Empty, owner, state, address)

    fun addTlbLabel(
        label: TlbLabel,
        initializeTlbField: (TvmState, UConcreteHeapRef, Int) -> Unit
    ): TlbStructureBuilder {
        // [label] must be deduced from store operations, and such labels have zero arity.
        // So, there is no need to support type arguments here.
        check(label.arity == 0) {
            "Only labels without arguments can be used in builder structures, but label $label has arity ${label.arity}"
        }
        return TlbStructureBuilder { suffix, owner, state, address ->
            val id = TlbStructureIdProvider.provideId()
            initializeTlbField(state, address, id)
            build(
                TlbStructure.KnownTypePrefix(
                    id = id,
                    typeLabel = label,
                    typeArgIds = emptyList(),
                    rest = suffix,
                    owner = owner
                ),
                owner,
                state,
                address
            )
        }
    }

    fun addConstant(
        ctx: TvmContext,
        bitString: String
    ): TlbStructureBuilder =
        TlbStructureBuilder { suffix, owner, state, address ->
            val id = TlbStructureIdProvider.provideId()
            val switchField = SwitchField(id, persistentListOf(), listOf(suffix.id))
            val sort = switchField.getSort(ctx)
            state.memory.writeField(address, switchField, sort, ctx.mkBv(0, sort), guard = ctx.trueExpr)
            build(
                TlbStructure.SwitchPrefix(
                    id = id,
                    switchSize = bitString.length,
                    givenVariants =
                        mapOf(
                            bitString to suffix
                        ),
                    owner
                ),
                owner,
                state,
                address
            )
        }

    companion object {
        val empty = TlbStructureBuilder { suffix, _, _, _ -> suffix }
    }
}
