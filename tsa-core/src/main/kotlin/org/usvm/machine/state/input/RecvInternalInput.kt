package org.usvm.machine.state.input

import org.ton.Endian
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.readField
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.MessageConcreteData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.asIntValue
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.allocEmptyBuilder
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.allocSliceFromData
import org.usvm.machine.state.builderStoreIntTlb
import org.usvm.machine.state.builderStoreSliceTlb
import org.usvm.machine.state.builderToCell
import org.usvm.machine.state.generateSymbolicAddressCell
import org.usvm.machine.state.generateSymbolicSlice
import org.usvm.machine.state.messages.Flags
import org.usvm.machine.state.messages.MessageAfterCommonMsgInfo
import org.usvm.machine.state.messages.TlbCommonMessageInfo
import org.usvm.machine.state.messages.TlbInternalMessageContent
import org.usvm.machine.state.readSliceCell
import org.usvm.sizeSort

class RecvInternalInput(
    state: TvmState,
    messageConcreteData: MessageConcreteData,
    receiverContractId: ContractId,
    givenMsgBody: UConcreteHeapRef? = null,
) : ReceiverInput(receiverContractId, messageConcreteData, givenMsgBody, state) {
    override val msgValue =
        with(state.ctx) {
            state.makeSymbolicPrimitive(mkBvSort(TvmContext.BITS_FOR_BALANCE)).zeroExtendToSort(int257sort)
        }

    override val srcAddressSlice =
        if (messageConcreteData.senderBits == null) {
            state.allocSliceFromCell(state.generateSymbolicAddressCell().first)
        } else {
            state.allocSliceFromData(
                state.ctx.mkBv(
                    messageConcreteData.senderBits,
                    TvmContext.stdMsgAddrSize.toUInt(),
                ),
            )
        }

    private val bouncedFlag =
        if (state.ctx.tvmOptions.analyzeBouncedMessaged) {
            state.makeSymbolicPrimitive(state.ctx.mkBvSort(1u))
        } else {
            state.ctx.mkBv(0, 1u)
        }

    // bounced:Bool
    override val bounced = state.ctx.mkEq(bouncedFlag, state.ctx.mkBv(1, 1u))

    private val msgBodyCellBounced: UConcreteHeapRef by lazy {
        with(state.ctx) {
            // hack for using builder operations
            val scope = TvmStepScopeManager(state, UForkBlackList.createDefault(), allowFailuresOnCurrentStep = false)

            val builder = state.allocEmptyBuilder()
            builderStoreIntTlb(
                scope,
                builder,
                builder,
                bouncedMessageTagLong.toBv257(),
                sizeBits = sizeExpr32,
                isSigned = false,
                endian = Endian.BigEndian,
            )
                ?: error("Cannot store bounced message prefix")

            // tail's length is up to 256 bits
            val tailSize = state.makeSymbolicPrimitive(mkBvSort(8u)).zeroExtendToSort(sizeSort)
            val tail = state.generateSymbolicSlice()
            val tailCell = state.memory.readField(tail, TvmContext.sliceCellField, addressSort) as UConcreteHeapRef
            state.fieldManagers.cellDataLengthFieldManager.writeCellDataLength(
                state,
                tailCell,
                tailSize,
                upperBound = 256,
            )
            state.fieldManagers.cellRefsLengthFieldManager.writeCellRefsLength(state, tailCell, zeroSizeExpr)
            builderStoreSliceTlb(scope, builder, builder, tail)
                ?: error("Cannot store bounced message tail")

            val stepResult = scope.stepResult()
            check(stepResult.originalStateAlive) {
                "Original state died while building bounced message"
            }
            check(stepResult.forkedStates.none()) {
                "Unexpected forks while building bounced message"
            }

            state.builderToCell(builder)
        }
    }

    private val msgBodySliceBounced: UHeapRef by lazy {
        state.allocSliceFromCell(msgBodyCellBounced)
    }

    override val msgBodySliceMaybeBounced: UHeapRef by lazy {
        state.ctx.mkIte(
            condition = bounced,
            trueBranch = { msgBodySliceBounced },
            falseBranch = { msgBodySliceNonBounced },
        )
    }

    // bounce:Bool
    // If bounced=true, then bounce must be false
    override val bounce =
        with(state.ctx) {
            bounced.not() and state.makeSymbolicPrimitive(state.ctx.boolSort)
        }

    val ihrDisabled = state.ctx.trueExpr // ihr_disabled:Bool
    val ihrFee = state.ctx.zeroValue // ihr_fee:Grams

    // fwd_fee:Grams
    override val fwdFee =
        with(state.ctx) {
            state.makeSymbolicPrimitive(mkBvSort(TvmContext.BITS_FOR_FWD_FEE)).zeroExtendToSort(int257sort)
        }

    override fun constructFullMessage(state: TvmState): UConcreteHeapRef =
        with(state.ctx) {
            // hack for using builder operations
            val scope = TvmStepScopeManager(state, UForkBlackList.createDefault(), allowFailuresOnCurrentStep = false)
            assertArgConstraints(scope, minMessageCurrencyValue = minMessageCurrencyValue)

            val flags = generateFlagsStruct(this)
            val bodyCellMaybeBounced =
                with(state) {
                    readSliceCell(msgBodySliceMaybeBounced)
                }
            val tlbMessageContent =
                TlbInternalMessageContent(
                    commonMessageInfo =
                        TlbCommonMessageInfo(
                            flags = flags,
                            srcAddressSlice = srcAddressSlice,
                            dstAddressSlice = contractAddressSlice,
                            msgValue = msgValue,
                            ihrFee = ihrFee,
                            fwdFee = fwdFee,
                            createdLt = createdLt,
                            createdAt = createdAt,
                        ),
                    messageAfterCommonMsgInfo =
                        MessageAfterCommonMsgInfo.ManuallyConstructed(
                            bodyCellMaybeBounced,
                            msgBodySliceMaybeBounced,
                        ),
                )

            val result = (
                tlbMessageContent.constructMessageCellFromContent(scope)?.fullMsgCell
                    ?: error("overflow during construction fo the full message in receive internal input")
            )

            val stepResult = scope.stepResult()
            check(stepResult.originalStateAlive) {
                "Original state died while building full message"
            }
            check(stepResult.forkedStates.none()) {
                "Unexpected forks while building full message"
            }
            return@with result
        }

    private fun generateFlagsStruct(ctx: TvmContext): Flags =
        with(ctx) {
            Flags(
                intMsgInfo = zeroValue,
                ihrDisabled = this@RecvInternalInput.ihrDisabled.asIntValue(),
                bounce = bounce.asIntValue(),
                bounced = bouncedFlag.zeroExtendToSort(int257sort),
            )
        }
}
