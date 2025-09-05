package org.usvm.machine.state.input

import org.ton.Endian
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.TvmConcreteGeneralData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.asIntValue
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.allocEmptyBuilder
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.allocSliceFromData
import org.usvm.machine.state.builderStoreGramsTlb
import org.usvm.machine.state.builderStoreIntTlb
import org.usvm.machine.state.builderStoreNextRef
import org.usvm.machine.state.builderStoreSliceTlb
import org.usvm.machine.state.builderToCell
import org.usvm.machine.state.generateSymbolicSlice
import org.usvm.machine.state.input.RecvInternalInput.MessageContent
import org.usvm.mkSizeExpr
import org.usvm.sizeSort

class RecvInternalInput(
    state: TvmState,
    concreteGeneralData: TvmConcreteGeneralData,
    receiverContractId: ContractId,
) : ReceiverInput(receiverContractId, concreteGeneralData, state) {
    override val msgValue = state.makeSymbolicPrimitive(state.ctx.int257sort)
    override val srcAddressSlice = if (concreteGeneralData.initialSenderBits == null) {
        state.generateSymbolicSlice()
    } else {
        state.allocSliceFromData(
            state.ctx.mkBv(
                concreteGeneralData.initialSenderBits,
                TvmContext.stdMsgAddrSize.toUInt()
            )
        )
    }

    // bounced:Bool
    val bounced = if (state.ctx.tvmOptions.analyzeBouncedMessaged) {
        state.makeSymbolicPrimitive(state.ctx.boolSort)
    } else {
        state.ctx.falseExpr
    }

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
                endian = Endian.BigEndian
            )
                ?: error("Cannot store bounced message prefix")

            // tail's length is up to 256 bits
            val tailSize = state.makeSymbolicPrimitive(mkBvSort(8u)).zeroExtendToSort(sizeSort)
            val tail = state.generateSymbolicSlice()
            val tailCell = state.memory.readField(tail, TvmContext.sliceCellField, addressSort)
            state.memory.writeField(tailCell, TvmContext.cellDataLengthField, sizeSort, tailSize, guard = trueExpr)
            state.memory.writeField(tailCell, TvmContext.cellRefsLengthField, sizeSort, zeroSizeExpr, guard = trueExpr)
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
    val bounce = with(state.ctx) {
        bounced.not() and state.makeSymbolicPrimitive(state.ctx.boolSort)
    }

    val ihrDisabled = state.ctx.trueExpr // ihr_disabled:Bool
    val ihrFee = state.ctx.zeroValue // ihr_fee:Grams
    val fwdFee = state.makeSymbolicPrimitive(state.ctx.int257sort) // fwd_fee:Grams

    data class MessageContent(
        val flags: UExpr<TvmInt257Sort>, // 4 bits
        val srcAddressSlice: UConcreteHeapRef,
        val dstAddressSlice: UConcreteHeapRef,
        val msgValue: UExpr<TvmInt257Sort>, //
        // assume currency collection is an empty dict (1 bit of zero)
        val ihrFee: UExpr<TvmInt257Sort>,
        val fwdFee: UExpr<TvmInt257Sort>,
        val createdLt: UExpr<TvmInt257Sort>, // 64
        val createdAt: UExpr<TvmInt257Sort>, // 32
        // init is (Maybe (Either StateInit ^StateInit)).nothing (1 bit of zero)
        val bodyDataSlice: UHeapRef, // assume body is (Either X ^X).left, prefix is 1 bit of one
    )


    override fun constructFullMessage(state: TvmState): UConcreteHeapRef = with(state.ctx) {
        val resultBuilder = state.allocEmptyBuilder()

        // hack for using builder operations
        val scope = TvmStepScopeManager(state, UForkBlackList.createDefault(), allowFailuresOnCurrentStep = false)
        assertArgConstraints(scope, minMessageCurrencyValue = minMessageCurrencyValue)

        val flags = generateFlags(this)

        val messageContent = MessageContent(
            flags = flags,
            srcAddressSlice = srcAddressSlice,
            dstAddressSlice = contractAddressSlice,
            msgValue = msgValue,
            ihrFee = ihrFee,
            fwdFee = fwdFee,
            createdLt = createdLt,
            createdAt = createdAt,
            bodyDataSlice = msgBodySliceMaybeBounced
        )
        return@with constructMessageFromContent(state, messageContent)
    }

    private fun generateFlags(ctx: TvmContext): UExpr<TvmInt257Sort> = with(ctx) {
        // int_msg_info$0
        var flags: UExpr<TvmInt257Sort> = zeroValue

        // ihr_disabled:Bool
        flags = mkBvShiftLeftExpr(flags, oneValue)
        flags = mkBvAddExpr(flags, ihrDisabled.asIntValue())

        // bounce:Bool
        flags = mkBvShiftLeftExpr(flags, oneValue)
        flags = mkBvAddExpr(flags, bounce.asIntValue())

        // bounced:Bool
        flags = mkBvShiftLeftExpr(flags, oneValue)
        flags = mkBvAddExpr(flags, bounced.asIntValue())

        return flags
    }
}


fun constructMessageFromContent(state: TvmState, content: MessageContent): UConcreteHeapRef =
    with(state.ctx) {
        val resultBuilder = state.allocEmptyBuilder()

        // hack for using builder operations
        val scope = TvmStepScopeManager(state, UForkBlackList.createDefault(), allowFailuresOnCurrentStep = false)

        builderStoreIntTlb(
            scope,
            resultBuilder,
            resultBuilder,
            content.flags,
            sizeBits = fourSizeExpr,
            isSigned = false,
            endian = Endian.BigEndian
        )
            ?: error("Cannot store flags")

        // src:MsgAddressInt
        builderStoreSliceTlb(scope, resultBuilder, resultBuilder, content.srcAddressSlice)
            ?: error("Cannot store src address")

        // dest:MsgAddressInt
        builderStoreSliceTlb(scope, resultBuilder, resultBuilder, content.dstAddressSlice)
            ?: error("Cannot store dest address")

        // value:CurrencyCollection
        // store message value
        builderStoreGramsTlb(scope, resultBuilder, resultBuilder, content.msgValue)
            ?: error("Cannot store message value")

        // extra currency collection --- an empty dict (a bit of zero)
        builderStoreIntTlb(
            scope,
            resultBuilder,
            resultBuilder,
            zeroValue,
            sizeBits = oneSizeExpr,
            isSigned = false,
            endian = Endian.BigEndian
        )
            ?: error("Cannot store extra currency collection")

        // ihr_fee:Grams
        builderStoreGramsTlb(scope, resultBuilder, resultBuilder, content.ihrFee)
            ?: error("Cannot store ihr fee")

        // fwd_fee:Gram
        builderStoreGramsTlb(scope, resultBuilder, resultBuilder, content.fwdFee)
            ?: error("Cannot store fwd fee")

        // created_lt:uint64
        builderStoreIntTlb(
            scope,
            resultBuilder,
            resultBuilder,
            content.createdLt,
            sizeBits = mkSizeExpr(64),
            isSigned = false,
            endian = Endian.BigEndian
        )
            ?: error("Cannot store created_lt")

        // created_at:uint32
        builderStoreIntTlb(
            scope,
            resultBuilder,
            resultBuilder,
            content.createdAt,
            sizeBits = mkSizeExpr(32),
            isSigned = false,
            endian = Endian.BigEndian
        )
            ?: error("Cannot store created_at")

        // init:(Maybe (Either StateInit ^StateInit)).nothing
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

        // body:(Either X ^X).left
        // set prefix of Either.left
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
            val msgBodyCell = memory.readField(content.bodyDataSlice, TvmContext.sliceCellField, addressSort)
            builderStoreNextRef(resultBuilder, msgBodyCell)
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
