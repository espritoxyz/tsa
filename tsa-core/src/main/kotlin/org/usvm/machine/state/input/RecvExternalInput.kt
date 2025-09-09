package org.usvm.machine.state.input

import org.ton.Endian
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.api.readField
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.TvmConcreteGeneralData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.allocEmptyBuilder
import org.usvm.machine.state.builderStoreIntTlb
import org.usvm.machine.state.builderStoreNextRefNoOverflowCheck
import org.usvm.machine.state.builderStoreSliceTlb
import org.usvm.machine.state.builderToCell

class RecvExternalInput(
    state: TvmState,
    concreteGeneralData: TvmConcreteGeneralData,
    receiverContractId: ContractId,
) : ReceiverInput(receiverContractId, concreteGeneralData, state) {
    private val ctx = state.ctx

    override val msgValue: UExpr<TvmContext.TvmInt257Sort>
        get() = ctx.zeroValue

    override val srcAddressSlice: UConcreteHeapRef?
        get() = null

    override val msgBodySliceMaybeBounced: UConcreteHeapRef
        get() = msgBodySliceNonBounced

    override fun constructFullMessage(state: TvmState): UConcreteHeapRef =
        with(ctx) {
            val resultBuilder = state.allocEmptyBuilder()

            // hack for using builder operations
            val scope = TvmStepScopeManager(state, UForkBlackList.createDefault(), allowFailuresOnCurrentStep = false)
            assertArgConstraints(scope, minMessageCurrencyValue = zeroValue)

            // ext_in_msg_info$10 src:MsgAddressExt (src is addr_none, which is 00)
            builderStoreIntTlb(
                scope,
                resultBuilder,
                resultBuilder,
                eightValue,
                sizeBits = fourSizeExpr,
                isSigned = false,
                endian = Endian.BigEndian
            )
                ?: error("Cannot store external message prefix")

            // dest:MsgAddressInt
            builderStoreSliceTlb(scope, resultBuilder, resultBuilder, contractAddressSlice)
                ?: error("Cannot store dest address")

            // import_fee:Grams
            builderStoreIntTlb(
                scope,
                resultBuilder,
                resultBuilder,
                zeroValue,
                sizeBits = fourSizeExpr,
                isSigned = false,
                endian = Endian.BigEndian
            )
                ?: error("Cannot store import_fee")

            // init:(Maybe (Either StateInit ^StateInit))
            // TODO: support StateInit?
            builderStoreIntTlb(
                scope,
                resultBuilder,
                resultBuilder,
                zeroValue,
                sizeBits = oneSizeExpr,
                isSigned = false,
                endian = Endian.BigEndian
            )
                ?: error("Cannot store init")

            // body:(Either X ^X)
            // TODO: support both formats?
            builderStoreIntTlb(
                scope,
                resultBuilder,
                resultBuilder,
                oneValue,
                sizeBits = oneSizeExpr,
                isSigned = false,
                endian = Endian.BigEndian
            )
                ?: error("Cannot store body")

            scope.doWithState {
                val msgBodyCell = memory.readField(msgBodySliceMaybeBounced, TvmContext.sliceCellField, addressSort)
                builderStoreNextRefNoOverflowCheck(resultBuilder, msgBodyCell)
            }

            val stepResult = scope.stepResult()
            check(stepResult.originalStateAlive) {
                "Original state died while building full message"
            }
            check(stepResult.forkedStates.none()) {
                "Unexpected forks while building full message"
            }

            return state.builderToCell(resultBuilder)
        }
}
