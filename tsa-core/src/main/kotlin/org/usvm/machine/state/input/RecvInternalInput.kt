package org.usvm.machine.state.input

import org.ton.Endian
import org.ton.bytecode.ADDRESS_PARAMETER_IDX
import org.usvm.UBoolExpr
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
import org.usvm.machine.state.doWithCtx
import org.usvm.machine.state.generateSymbolicSlice
import org.usvm.machine.state.getBalanceOf
import org.usvm.machine.state.getContractInfoParamOf
import org.usvm.machine.state.slicePreloadDataBits
import org.usvm.machine.state.unsignedIntegerFitsBits
import org.usvm.mkSizeExpr
import org.usvm.sizeSort

class RecvInternalInput(
    state: TvmState,
    private val concreteGeneralData: TvmConcreteGeneralData,
    private val contractId: ContractId,
) : ReceiverInput {
    override val msgBodySliceNonBounced = state.generateSymbolicSlice()  // used only in non-bounced messages
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

    val msgBodySliceMaybeBounced: UHeapRef by lazy {
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

    val ihrDisabled = state.makeSymbolicPrimitive(state.ctx.boolSort) // ihr_disabled:Bool
    val ihrFee = state.makeSymbolicPrimitive(state.ctx.int257sort) // ihr_fee:Grams
    val fwdFee = state.makeSymbolicPrimitive(state.ctx.int257sort) // fwd_fee:Grams
    val createdLt = state.makeSymbolicPrimitive(state.ctx.int257sort) // created_lt:uint64
    val createdAt = state.makeSymbolicPrimitive(state.ctx.int257sort) // created_at:uint32

    override val contractAddressSlice: UConcreteHeapRef by lazy {
        state.allocSliceFromCell(contractAddressCell)
    }

    private fun assertArgConstraints(scope: TvmStepScopeManager): Unit? {
        val constraint = scope.doWithCtx {
            val msgValueConstraint = mkAnd(
                mkBvSignedLessOrEqualExpr(minMessageCurrencyValue, msgValue),
                mkBvSignedLessOrEqualExpr(msgValue, maxMessageCurrencyValue)
            )

            val createdLtConstraint = unsignedIntegerFitsBits(createdLt, bits = 64u)
            val createdAtConstraint = unsignedIntegerFitsBits(createdAt, bits = 32u)

            val balanceConstraints = mkBalanceConstraints(scope)

            val opcodeConstraint = if (concreteGeneralData.initialOpcode != null) {
                val msgBodyCell = scope.calcOnState {
                    memory.readField(msgBodySliceNonBounced, TvmContext.sliceCellField, addressSort)
                }
                val msgBodyCellSize = scope.calcOnState {
                    memory.readField(msgBodyCell, TvmContext.cellDataLengthField, sizeSort)
                }
                val sizeConstraint = mkBvSignedGreaterOrEqualExpr(msgBodyCellSize, mkSizeExpr(TvmContext.OP_BITS.toInt()))

                // TODO: use TL-B?
                val opcode = scope.slicePreloadDataBits(msgBodySliceNonBounced, TvmContext.OP_BITS.toInt())
                    ?: error("Cannot read opcode from initial msgBody")

                val opcodeConstraint = opcode eq mkBv(concreteGeneralData.initialOpcode.toLong(), TvmContext.OP_BITS)

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
                opcodeConstraint,
            )
        }

        return scope.assert(
            constraint,
            unsatBlock = { error("Cannot assert recv_internal constraints") },
            unknownBlock = { error("Unknown result while asserting recv_internal constraints") }
        )
    }

    private fun TvmContext.mkBalanceConstraints(scope: TvmStepScopeManager): UBoolExpr {
        val balance = scope.calcOnState { getBalanceOf(contractId) }
            ?: error("Unexpected incorrect config balance value")

        val balanceConstraints = mkAnd(
            mkBvSignedLessOrEqualExpr(balance, maxMessageCurrencyValue),
            mkBvSignedLessOrEqualExpr(minMessageCurrencyValue, msgValue),
            mkBvSignedLessOrEqualExpr(msgValue, balance),
        )

        return balanceConstraints
    }

    fun constructFullMessage(state: TvmState): UConcreteHeapRef = with(state.ctx) {
        val resultBuilder = state.allocEmptyBuilder()

        // hack for using builder operations
        val scope = TvmStepScopeManager(state, UForkBlackList.createDefault(), allowFailuresOnCurrentStep = false)
        assertArgConstraints(scope)

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
