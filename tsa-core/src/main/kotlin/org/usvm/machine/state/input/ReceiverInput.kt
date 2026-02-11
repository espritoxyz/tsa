package org.usvm.machine.state.input

import org.ton.bytecode.ADDRESS_PARAMETER_IDX
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.readField
import org.usvm.machine.ConcreteOpcode
import org.usvm.machine.ExcludedOpcodes
import org.usvm.machine.MessageConcreteData
import org.usvm.machine.NoOpcode
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.sliceCellField
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.doWithCtx
import org.usvm.machine.state.generateSymbolicSlice
import org.usvm.machine.state.getBalanceOf
import org.usvm.machine.state.getContractInfoParamOf
import org.usvm.machine.state.sliceLoadIntTlbNoFork
import org.usvm.machine.state.unsignedIntegerFitsBits
import org.usvm.mkSizeExpr

sealed class ReceiverInput(
    private val receiverContractId: ContractId,
    private val messageConcreteData: MessageConcreteData,
    givenMsgBody: UConcreteHeapRef? = null,
    state: TvmState,
) : TvmInput {
    abstract val msgValue: UExpr<TvmInt257Sort>
    abstract val msgBodySliceMaybeBounced: UHeapRef
    abstract val srcAddressSlice: UConcreteHeapRef? // null for external messages
    abstract val fwdFee: UExpr<TvmInt257Sort>? // null for external messages
    abstract val bounce: UBoolExpr
    abstract val bounced: UBoolExpr

    abstract fun constructFullMessage(state: TvmState): UConcreteHeapRef?

    val msgBodySliceNonBounced = givenMsgBody ?: state.generateSymbolicSlice()

    val createdLt = state.makeSymbolicPrimitive(state.ctx.int257sort) // created_lt:uint64
    val createdAt = state.makeSymbolicPrimitive(state.ctx.int257sort) // created_at:uint32

    private val contractAddressCell: UConcreteHeapRef by lazy {
        state.getContractInfoParamOf(ADDRESS_PARAMETER_IDX, receiverContractId).cellValue as? UConcreteHeapRef
            ?: error("Cannot extract contract address")
    }

    val contractAddressSlice: UConcreteHeapRef by lazy {
        state.allocSliceFromCell(contractAddressCell)
    }

    val addressSlices: List<UConcreteHeapRef> by lazy {
        listOf(contractAddressSlice) + (srcAddressSlice?.let { listOf(it) } ?: emptyList())
    }

    protected fun assertArgConstraints(
        scope: TvmStepScopeManager,
        minMessageCurrencyValue: UExpr<TvmInt257Sort>,
    ): Unit? {
        val constraint =
            scope.doWithCtx {
                check(!scope.allowFailuresOnCurrentStep) {
                    "Expected scope not to allow failures"
                }

                val msgValueConstraint =
                    mkAnd(
                        mkBvSignedLessOrEqualExpr(minMessageCurrencyValue, msgValue),
                        mkBvSignedLessOrEqualExpr(msgValue, maxMessageCurrencyValue),
                    )

                val createdLtConstraint = unsignedIntegerFitsBits(createdLt, bits = 64u)
                val createdAtConstraint = unsignedIntegerFitsBits(createdAt, bits = 32u)

                val balanceConstraints = mkBalanceConstraints(scope, minMessageCurrencyValue)

                val msgBodyCell =
                    scope.calcOnState {
                        memory.readField(msgBodySliceNonBounced, sliceCellField, addressSort)
                    }
                val msgBodyCellSize =
                    scope.calcOnState {
                        fieldManagers.cellDataLengthFieldManager.readCellDataLength(this, msgBodyCell)
                    }

                val opcodeLength = TvmContext.OP_BITS.toInt()

                val opcodeConstraint =
                    when (messageConcreteData.opcodeInfo) {
                        is ExcludedOpcodes -> {
                            val (_, opcode) =
                                sliceLoadIntTlbNoFork(scope, msgBodySliceNonBounced, sizeBits = opcodeLength)
                                    ?: return@doWithCtx null

                            messageConcreteData.opcodeInfo.opcodes.fold(trueExpr as UBoolExpr) { acc, value ->
                                acc and (opcode neq value.toBv257())
                            }
                        }

                        is ConcreteOpcode -> {
                            val (_, opcode) =
                                sliceLoadIntTlbNoFork(scope, msgBodySliceNonBounced, sizeBits = opcodeLength)
                                    ?: return@doWithCtx null

                            opcode eq messageConcreteData.opcodeInfo.opcode.toBv257()
                        }

                        is NoOpcode -> {
                            mkBvSignedLessExpr(msgBodyCellSize, mkSizeExpr(opcodeLength))
                        }

                        null -> {
                            trueExpr
                        }
                    }

                val fwdFeeConstraint =
                    if (fwdFee != null) {
                        val high = mkBvSignedLessOrEqualExpr(fwdFee!!, TvmContext.MAX_FWD_FEE.toBv257())
                        val low = mkBvSignedGreaterOrEqualExpr(fwdFee!!, zeroValue)
                        low and high
                    } else {
                        trueExpr
                    }

                mkAnd(
                    msgValueConstraint,
                    createdLtConstraint,
                    createdAtConstraint,
                    balanceConstraints,
                    opcodeConstraint,
                    fwdFeeConstraint,
                )
            } ?: return null

        return scope.assert(
            constraint,
            unsatBlock = {
                error("Cannot assert recv_internal constraints")
            },
            unknownBlock = { error("Unknown result while asserting recv_internal constraints") },
        )
    }

    private fun TvmContext.mkBalanceConstraints(
        scope: TvmStepScopeManager,
        minMessageCurrencyValue: UExpr<TvmInt257Sort>,
    ): UBoolExpr {
        val balance =
            scope.calcOnState { getBalanceOf(receiverContractId) }
                ?: error("Unexpected incorrect config balance value")

        val balanceConstraints =
            mkAnd(
                mkBvSignedLessOrEqualExpr(balance, maxMessageCurrencyValue),
                mkBvSignedLessOrEqualExpr(minMessageCurrencyValue, msgValue),
                mkBvSignedLessOrEqualExpr(msgValue, balance),
            )

        return balanceConstraints
    }
}
