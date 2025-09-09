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
import org.usvm.machine.state.builderStoreNextRefNoOverflowCheck
import org.usvm.machine.state.builderStoreSliceTlb
import org.usvm.machine.state.builderToCell
import org.usvm.machine.state.generateSymbolicSlice
import org.usvm.mkSizeExpr
import org.usvm.sizeSort

class RecvInternalInput(
    state: TvmState,
    concreteGeneralData: TvmConcreteGeneralData,
    contractId: ContractId,
) : ReceiverInput(contractId, concreteGeneralData, state) {
    override val msgValue = state.makeSymbolicPrimitive(state.ctx.int257sort)
    override val srcAddressSlice = if (concreteGeneralData.initialSenderBits == null) {
        state.generateSymbolicSlice()
    } else {
        state.allocSliceFromData(state.ctx.mkBv(concreteGeneralData.initialSenderBits, TvmContext.stdMsgAddrSize.toUInt()))
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
            builderStoreIntTlb(scope, builder, builder, bouncedMessageTagLong.toBv257(), sizeBits = sizeExpr32, isSigned = false, endian = Endian.BigEndian)
                ?: error("Cannot store bounced message prefix")

            // tail's length is up to 256 bits
            val tailSize = state.makeSymbolicPrimitive(mkBvSort(8u)).zeroExtendToSort(sizeSort)
            val tail = state.generateSymbolicSlice()
            val tailCell = state.memory.readField(tail, TvmContext.sliceCellField, addressSort) as UConcreteHeapRef
            state.fieldManagers.cellDataLengthFieldManager.writeCellDataLength(state, tailCell, tailSize, upperBound = 256)
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

    override fun constructFullMessage(state: TvmState): UConcreteHeapRef = with(state.ctx) {
        val resultBuilder = state.allocEmptyBuilder()

        // hack for using builder operations
        val scope = TvmStepScopeManager(state, UForkBlackList.createDefault(), allowFailuresOnCurrentStep = false)
        assertArgConstraints(scope, minMessageCurrencyValue = minMessageCurrencyValue)

        val flags = generateFlags(this)

        builderStoreIntTlb(scope, resultBuilder, resultBuilder, flags, sizeBits = fourSizeExpr, isSigned = false, endian = Endian.BigEndian)
            ?: error("Cannot store flags")

        // src:MsgAddressInt
        builderStoreSliceTlb(scope, resultBuilder, resultBuilder, srcAddressSlice)
            ?: error("Cannot store src address")

        // dest:MsgAddressInt
        builderStoreSliceTlb(scope, resultBuilder, resultBuilder, contractAddressSlice)
            ?: error("Cannot store dest address")

        // value:CurrencyCollection
        // store message value
        builderStoreGramsTlb(scope, resultBuilder, resultBuilder, msgValue)
            ?: error("Cannot store message value")

        // extra currency collection
        builderStoreIntTlb(scope, resultBuilder, resultBuilder, zeroValue, sizeBits = oneSizeExpr, isSigned = false, endian = Endian.BigEndian)
            ?: error("Cannot store extra currency collection")

        // ihr_fee:Grams
        builderStoreGramsTlb(scope, resultBuilder, resultBuilder, ihrFee)
            ?: error("Cannot store ihr fee")

        // fwd_fee:Gram
        builderStoreGramsTlb(scope, resultBuilder, resultBuilder, fwdFee)
            ?: error("Cannot store fwd fee")

        // created_lt:uint64
        builderStoreIntTlb(scope, resultBuilder, resultBuilder, createdLt, sizeBits = mkSizeExpr(64), isSigned = false, endian = Endian.BigEndian)
            ?: error("Cannot store created_lt")

        // created_at:uint32
        builderStoreIntTlb(scope, resultBuilder, resultBuilder, createdAt, sizeBits = mkSizeExpr(32), isSigned = false, endian = Endian.BigEndian)
            ?: error("Cannot store created_at")

        // init:(Maybe (Either StateInit ^StateInit))
        // TODO: support StateInit?
        builderStoreIntTlb(scope, resultBuilder, resultBuilder, zeroValue, sizeBits = oneSizeExpr, isSigned = false, endian = Endian.BigEndian)
            ?: error("Cannot store init")

        // body:(Either X ^X)
        // TODO: support both formats?
        builderStoreIntTlb(scope, resultBuilder, resultBuilder, oneValue, sizeBits = oneSizeExpr, isSigned = false, endian = Endian.BigEndian)
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
