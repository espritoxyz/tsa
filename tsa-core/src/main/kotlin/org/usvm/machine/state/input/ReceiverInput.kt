package org.usvm.machine.state.input

import org.ton.bytecode.ADDRESS_PARAMETER_IDX
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.readField
import org.usvm.machine.TvmConcreteGeneralData
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
import org.usvm.machine.state.slicePreloadDataBits
import org.usvm.machine.state.unsignedIntegerFitsBits
import org.usvm.mkSizeExpr

sealed class ReceiverInput(
    protected val contractId: ContractId,
    private val concreteGeneralData: TvmConcreteGeneralData,
    state: TvmState,
) : TvmInput {
    abstract val msgValue: UExpr<TvmInt257Sort>
    abstract val msgBodySliceMaybeBounced: UHeapRef
    abstract val srcAddressSlice: UConcreteHeapRef? // null for external messages

    abstract fun constructFullMessage(state: TvmState): UConcreteHeapRef

    val msgBodySliceNonBounced = state.generateSymbolicSlice()

    val createdLt = state.makeSymbolicPrimitive(state.ctx.int257sort) // created_lt:uint64
    val createdAt = state.makeSymbolicPrimitive(state.ctx.int257sort) // created_at:uint32

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

    protected fun assertArgConstraints(
        scope: TvmStepScopeManager,
        minMessageCurrencyValue: UExpr<TvmInt257Sort>,
    ): Unit? {
        val constraint =
            scope.doWithCtx {
                val msgValueConstraint =
                    mkAnd(
                        mkBvSignedLessOrEqualExpr(minMessageCurrencyValue, msgValue),
                        mkBvSignedLessOrEqualExpr(msgValue, maxMessageCurrencyValue)
                    )

                val createdLtConstraint = unsignedIntegerFitsBits(createdLt, bits = 64u)
                val createdAtConstraint = unsignedIntegerFitsBits(createdAt, bits = 32u)

                val balanceConstraints = mkBalanceConstraints(scope, minMessageCurrencyValue)

                val opcodeConstraint =
                    if (concreteGeneralData.initialOpcode != null) {
                        val msgBodyCell =
                            scope.calcOnState {
                                memory.readField(msgBodySliceNonBounced, sliceCellField, addressSort)
                            }
                        val msgBodyCellSize =
                            scope.calcOnState {
                                fieldManagers.cellDataLengthFieldManager.readCellDataLength(this, msgBodyCell)
                            }
                        val sizeConstraint =
                            mkBvSignedGreaterOrEqualExpr(msgBodyCellSize, mkSizeExpr(TvmContext.OP_BITS.toInt()))

                        // TODO: use TL-B?
                        val opcode =
                            scope.slicePreloadDataBits(msgBodySliceNonBounced, TvmContext.OP_BITS.toInt())
                                ?: error("Cannot read opcode from initial msgBody")

                        val opcodeConstraint =
                            opcode eq mkBv(concreteGeneralData.initialOpcode.toLong(), TvmContext.OP_BITS)

                        sizeConstraint and opcodeConstraint
                    } else {
                        trueExpr
                    }

                // TODO any other constraints?

                mkAnd(
                    msgValueConstraint,
                    createdLtConstraint,
                    createdAtConstraint,
                    balanceConstraints,
                    opcodeConstraint
                )
            }

        return scope.assert(
            constraint,
            unsatBlock = { error("Cannot assert recv_internal constraints") },
            unknownBlock = { error("Unknown result while asserting recv_internal constraints") }
        )
    }

    private fun TvmContext.mkBalanceConstraints(
        scope: TvmStepScopeManager,
        minMessageCurrencyValue: UExpr<TvmInt257Sort>,
    ): UBoolExpr {
        val balance =
            scope.calcOnState { getBalanceOf(contractId) }
                ?: error("Unexpected incorrect config balance value")

        val balanceConstraints =
            mkAnd(
                mkBvSignedLessOrEqualExpr(balance, maxMessageCurrencyValue),
                mkBvSignedLessOrEqualExpr(minMessageCurrencyValue, msgValue),
                mkBvSignedLessOrEqualExpr(msgValue, balance)
            )

        return balanceConstraints
    }
}
