package org.usvm.machine.state.input

import org.ton.bytecode.ADDRESS_PARAMETER_IDX
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.sliceCellField
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.getContractInfoParamOf

sealed class ReceiverInput(
    contractId: ContractId,
    state: TvmState,
) : TvmInput {
    abstract val msgValue: UExpr<TvmContext.TvmInt257Sort>
    abstract val msgBodySliceNonBounced: UConcreteHeapRef
    abstract val srcAddressSlice: UConcreteHeapRef?  // null for external messages

    val contractAddressCell: UConcreteHeapRef by lazy {
        state.getContractInfoParamOf(ADDRESS_PARAMETER_IDX, contractId).cellValue as? UConcreteHeapRef
            ?: error("Cannot extract contract address")
    }

    val contractAddressSlice: UConcreteHeapRef by lazy {
        state.allocSliceFromCell(contractAddressCell)
    }

    val srcAddressCell: UConcreteHeapRef? by lazy {
        srcAddressSlice?.let {
            state.memory.readField(it, sliceCellField, state.ctx.addressSort) as UConcreteHeapRef
        }
    }

    val addressSlices: List<UConcreteHeapRef> by lazy {
        listOf(contractAddressSlice) + (srcAddressSlice?.let { listOf(it) } ?: emptyList())
    }
}
