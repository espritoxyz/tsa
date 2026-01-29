package org.usvm.machine.state.messages

import io.ksmt.utils.uncheckedCast
import org.ton.Endian
import org.ton.bytecode.ADDRESS_PARAMETER_IDX
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.INT_BITS
import org.usvm.machine.TvmContext.Companion.NONE_ADDRESS_TAG
import org.usvm.machine.TvmContext.Companion.STD_ADDRESS_TAG
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.allocEmptyBuilder
import org.usvm.machine.state.allocEmptyCell
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.builderStoreGramsTlb
import org.usvm.machine.state.builderStoreIntTlb
import org.usvm.machine.state.builderStoreNextRefNoOverflowCheck
import org.usvm.machine.state.builderStoreSliceTlb
import org.usvm.machine.state.builderStoreSliceTransaction
import org.usvm.machine.state.builderToCell
import org.usvm.machine.state.createSliceIsEmptyConstraint
import org.usvm.machine.state.doWithCtx
import org.usvm.machine.state.getCellContractInfoParam
import org.usvm.machine.state.readCellDataLength
import org.usvm.machine.state.readSliceCell
import org.usvm.machine.state.readSliceDataPos
import org.usvm.machine.state.sliceLoadAddrTransaction
import org.usvm.machine.state.sliceLoadGramsTransaction
import org.usvm.machine.state.sliceLoadIntTransaction
import org.usvm.machine.state.sliceLoadRefTransaction
import org.usvm.mkSizeExpr
import org.usvm.test.resolver.TvmTestStateResolver

data class TlbCommonMessageInfo(
    val flags: Flags, // 4 bits
    val srcAddressSlice: UHeapRef?,
    val dstAddressSlice: UHeapRef,
    val msgValue: UExpr<TvmContext.TvmInt257Sort>,
    // assume currency collection is an empty dict (1 bit of zero)
    val ihrFee: UExpr<TvmContext.TvmInt257Sort>,
    val fwdFee: UExpr<TvmContext.TvmInt257Sort>,
    val createdLt: UExpr<TvmContext.TvmInt257Sort>, // 64
    val createdAt: UExpr<TvmContext.TvmInt257Sort>, // 32
) {
    companion object {
        fun extractFromSlice(
            scope: TvmStepScopeManager,
            ptr: ParsingState,
            quietBlock: (TvmState.() -> Unit)? = null,
        ): TlbCommonMessageInfo? =
            with(scope.ctx) {
                // int_msg_info$0 ihr_disabled:Bool bounce:Bool bounced:Bool
                val tlbFlags =
                    (0 until 4).map {
                        val curFlag =
                            sliceLoadIntTransaction(scope, ptr.slice, 1, quietBlock = quietBlock)?.unwrap(ptr)
                                ?: return@with null
                        curFlag
                    }

                val srcAddSlice =
                    (
                        sliceSkipNoneOrStdAddr(scope, ptr.slice, quietBlock = quietBlock)
                            ?: return@with null
                    ).unwrap(ptr)

                val addrCell =
                    scope.getCellContractInfoParam(ADDRESS_PARAMETER_IDX)
                        ?: return null
                val tlbAddrSlice = scope.calcOnState { allocSliceFromCell(addrCell) }
                scope.calcOnState {
                    dataCellInfoStorage.mapper.addAddressSlice(tlbAddrSlice)
                }

                // dest:MsgAddressInt
                val tlbDestSlice =
                    sliceLoadAddrTransaction(scope, ptr.slice, quietBlock)?.unwrap(ptr)
                        ?: return@with null

                val destSliceSize =
                    with(scope.ctx) {
                        val position = scope.calcOnState { readSliceDataPos(tlbDestSlice) }
                        val cellSize = scope.calcOnState { readCellDataLength(readSliceCell(tlbDestSlice)) }
                        cellSize bvSub position
                    }
                scope.fork(
                    condition = with(scope.ctx) { destSliceSize bvUgt 2.toSizeSort() },
                    falseStateIsExceptional = false, // soft failure
                    blockOnFalseState = { throwBadDestinationAddress(this) },
                ) ?: return null

                // value:CurrencyCollection
                val tlbSymbolicMsgValue =
                    sliceLoadGramsTransaction(scope, ptr.slice, quietBlock = quietBlock)?.unwrap(ptr)
                        ?: return@with null

                val tlbExtraCurrenciesBit =
                    sliceLoadIntTransaction(scope, ptr.slice, 1, quietBlock = quietBlock)?.unwrap(ptr)
                        ?: return@with null

                val extraCurrenciesEmptyConstraint = tlbExtraCurrenciesBit eq zeroValue
                scope.assert(extraCurrenciesEmptyConstraint)
                    ?: return@with null

                // extra_currency_bit
                val ihrFee =
                    sliceLoadGramsTransaction(scope, ptr.slice, quietBlock)?.unwrap(ptr)
                        ?: return@with null

                // fwd_fee:Grams
                val fwdFee =
                    sliceLoadGramsTransaction(scope, ptr.slice, quietBlock)?.unwrap(ptr)
                        ?: return@with null

                // created_lt:uint64 created_at:uint32
                val createdLt =
                    sliceLoadIntTransaction(scope, ptr.slice, 64, quietBlock = quietBlock)?.unwrap(ptr)
                        ?: return@with null
                val createdAt =
                    sliceLoadIntTransaction(scope, ptr.slice, 32)?.unwrap(ptr)
                        ?: return@with null

                TlbCommonMessageInfo(
                    flags = Flags(tlbFlags[0], tlbFlags[1], tlbFlags[2], tlbFlags[3]),
                    srcAddressSlice = srcAddSlice,
                    dstAddressSlice = tlbDestSlice,
                    msgValue = tlbSymbolicMsgValue,
                    ihrFee = ihrFee,
                    fwdFee = fwdFee,
                    createdLt = createdLt,
                    createdAt = createdAt,
                )
            }

        private fun sliceSkipNoneOrStdAddr(
            scope: TvmStepScopeManager,
            slice: UHeapRef,
            quietBlock: (TvmState.() -> Unit)?,
        ): Pair<UHeapRef, UHeapRef?>? =
            scope.doWithCtx {
                val (afterTagSlice, tag) =
                    sliceLoadIntTransaction(scope, slice, 2)
                        ?: return@doWithCtx null

                val noneTag = mkBv(NONE_ADDRESS_TAG, INT_BITS)
                val isTagNone =
                    scope.checkCondition(tag eq noneTag.uncheckedCast())
                        ?: return@doWithCtx null

                if (isTagNone) {
                    return@doWithCtx afterTagSlice to null
                }

                // TODO not fallback to old memory
                val stdTag = mkBv(STD_ADDRESS_TAG, INT_BITS)
                val isTagStd =
                    scope.checkCondition(tag eq stdTag.uncheckedCast())
                        ?: return@doWithCtx null

                require(isTagStd) {
                    "Only none and std source addresses are supported"
                }

                val (nextSlice, addr) =
                    sliceLoadAddrTransaction(scope, slice, quietBlock = quietBlock)
                        ?: return@doWithCtx null

                nextSlice to addr
            }

        private fun TvmStepScopeManager.checkCondition(cond: UBoolExpr): Boolean? =
            with(ctx) {
                val checkRes = checkSat(cond)
                val invertedRes = checkSat(cond.not())

                require(checkRes == null || invertedRes == null) {
                    error("Symbolic actions are not supported")
                }

                if (checkRes == null && invertedRes == null) {
                    return null
                }

                checkRes != null
            }
    }
}

/**
 * Contains the part of the message after the CommonMsgInfo
 */
sealed interface MessageAfterCommonMsgInfo {
    /**
     * Is the original cell where the body was contained (null if was an inlined slice)
     */
    val bodyOriginalRef: UHeapRef?
    val stateInitRef: UHeapRef?

    /**
     * Contains empty state init and a body as a not-inline cell [bodyCell].
     * @param bodySlice is a view on the [bodyCell]
     */
    data class ManuallyConstructed(
        // init is assumed to be (Maybe (Either StateInit ^StateInit)).nothing (1 bit of zero)
        val bodyCell: UHeapRef, // assume body is (Either X ^X).left, prefix is 1 bit of one
        val bodySlice: UHeapRef,
    ) : MessageAfterCommonMsgInfo {
        override val stateInitRef: UHeapRef?
            get() = null
        override val bodyOriginalRef: UHeapRef?
            get() = null
    }

    /**
     * @param tailSlice empbodies the whole slice of the message that follows the CommonMsgInfo
     */
    data class ConstructedBySomeContract(
        val tailSlice: UHeapRef,
        val bodySlice: UConcreteHeapRef, // all the message after the CommonMessageInfo
        override val stateInitRef: UHeapRef?,
        override val bodyOriginalRef: UHeapRef?,
    ) : MessageAfterCommonMsgInfo
}

fun MessageAfterCommonMsgInfo.bodySlice() =
    when (this) {
        is MessageAfterCommonMsgInfo.ManuallyConstructed -> this.bodySlice
        is MessageAfterCommonMsgInfo.ConstructedBySomeContract -> this.bodySlice
    }

data class ConstructedMessageCells(
    val msgBodySlice: UHeapRef,
    val fullMsgCell: UConcreteHeapRef,
)

/**
 * Use this class with caution, as the structure it contains might cause cell overflow
 */
data class TlbInternalMessageContent(
    val commonMessageInfo: TlbCommonMessageInfo,
    val messageAfterCommonMsgInfo: MessageAfterCommonMsgInfo,
) {
    val bodyOriginalRef: UHeapRef?
        get() = messageAfterCommonMsgInfo.bodyOriginalRef
    val stateInitRef: UHeapRef?
        get() = messageAfterCommonMsgInfo.stateInitRef

    /**
     * @return `null` iff the message failed to construct due to an overflow
     */
    fun constructMessageCellFromContent(
        scope: TvmStepScopeManager,
        quietBlock: (TvmState.() -> Unit)? = null,
    ): ConstructedMessageCells? {
        val state = scope.calcOnState { this }
        return with(state.ctx) {
            val resultBuilder = state.allocEmptyBuilder()

            val commonMessageInfo = this@TlbInternalMessageContent.commonMessageInfo
            for (flag in commonMessageInfo.flags.asFlagsList()) {
                builderStoreIntTlb(
                    scope,
                    resultBuilder,
                    resultBuilder,
                    flag,
                    sizeBits = oneSizeExpr,
                    isSigned = false,
                    endian = Endian.BigEndian,
                )
                    ?: error("Cannot store flags")
            }

            // src:MsgAddressInt
            builderStoreSliceTlb(
                scope,
                resultBuilder,
                resultBuilder,
                commonMessageInfo.srcAddressSlice ?: error("null slice"),
            )
                ?: error("Cannot store src address")

            // dest:MsgAddressInt
            builderStoreSliceTlb(scope, resultBuilder, resultBuilder, commonMessageInfo.dstAddressSlice)
                ?: error("Cannot store dest address")

            // value:CurrencyCollection
            // store message value
            builderStoreGramsTlb(scope, resultBuilder, resultBuilder, commonMessageInfo.msgValue)
                ?: error("Cannot store message value")

            // extra currency collection --- an empty dict (a bit of zero)
            builderStoreIntTlb(
                scope,
                resultBuilder,
                resultBuilder,
                zeroValue,
                sizeBits = oneSizeExpr,
                isSigned = false,
                endian = Endian.BigEndian,
            )
                ?: error("Cannot store extra currency collection")

            // ihr_fee:Grams
            builderStoreGramsTlb(scope, resultBuilder, resultBuilder, commonMessageInfo.ihrFee)
                ?: error("Cannot store ihr fee")

            // fwd_fee:Gram
            builderStoreGramsTlb(scope, resultBuilder, resultBuilder, commonMessageInfo.fwdFee)
                ?: error("Cannot store fwd fee")

            // created_lt:uint64
            builderStoreIntTlb(
                scope,
                resultBuilder,
                resultBuilder,
                commonMessageInfo.createdLt,
                sizeBits = mkSizeExpr(64),
                isSigned = false,
                endian = Endian.BigEndian,
            )
                ?: error("Cannot store created_lt")

            // created_at:uint32
            builderStoreIntTlb(
                scope,
                resultBuilder,
                resultBuilder,
                commonMessageInfo.createdAt,
                sizeBits = mkSizeExpr(32),
                isSigned = false,
                endian = Endian.BigEndian,
            )
                ?: error("Cannot store created_at")

            val bodySlice =
                when (val tail = this@TlbInternalMessageContent.messageAfterCommonMsgInfo) {
                    is MessageAfterCommonMsgInfo.ManuallyConstructed -> {
                        // init:(Maybe (Either StateInit ^StateInit)).nothing
                        builderStoreIntTlb(
                            scope,
                            resultBuilder,
                            resultBuilder,
                            zeroValue,
                            sizeBits = oneSizeExpr,
                            isSigned = false,
                            endian = Endian.BigEndian,
                        )
                            ?: error("Cannot store init")

                        // body:(Either X ^X).right
                        // set prefix of Either.right
                        builderStoreIntTlb(
                            scope,
                            resultBuilder,
                            resultBuilder,
                            oneValue,
                            sizeBits = oneSizeExpr,
                            isSigned = false,
                            endian = Endian.BigEndian,
                        )
                            ?: error("Cannot store body")
                        scope.doWithState {
                            builderStoreNextRefNoOverflowCheck(resultBuilder, tail.bodyCell)
                        }
                        tail.bodySlice
                    }

                    is MessageAfterCommonMsgInfo.ConstructedBySomeContract -> {
                        builderStoreSliceTlb(
                            scope,
                            resultBuilder,
                            resultBuilder,
                            tail.tailSlice,
                            quietBlock,
                        ) ?: return@with null
                        tail.bodySlice
                    }
                }

            val fullMessageCell = state.builderToCell(resultBuilder)
            return ConstructedMessageCells(msgBodySlice = bodySlice, fullMsgCell = fullMessageCell)
        }
    }

    companion object {
        fun extractFromSlice(
            scope: TvmStepScopeManager,
            ptr: ParsingState,
            resolver: TvmTestStateResolver,
            quietBlock: (TvmState.() -> Unit)?,
        ): TlbInternalMessageContent? =
            with(scope.ctx) {
                val commonMessageInfo =
                    TlbCommonMessageInfo.extractFromSlice(scope, ptr, quietBlock)
                        ?: return null

                val tailSlice = ptr.slice

                val stateInitRef =
                    loadStateInit(scope, resolver, ptr, quietBlock).getOrElse { return@with null }

                val (bodyCellOriginal, bodyCell) =
                    loadBody(scope, resolver, ptr, quietBlock)
                        .getOrElse { return@with null }

                val bodySlice = scope.calcOnState { allocSliceFromCell(bodyCell) }
                val messageAfterCommonMsgInfo =
                    MessageAfterCommonMsgInfo.ConstructedBySomeContract(
                        tailSlice,
                        bodySlice,
                        stateInitRef,
                        bodyCellOriginal,
                    )

                val sliceFullyParsed = scope.calcOnState { createSliceIsEmptyConstraint(ptr.slice) }
                scope.fork(
                    sliceFullyParsed,
                    falseStateIsExceptional = quietBlock == null,
                    blockOnFalseState = quietBlock ?: throwCellOverflowError,
                ) ?: return@with null
                TlbInternalMessageContent(
                    commonMessageInfo = commonMessageInfo,
                    messageAfterCommonMsgInfo = messageAfterCommonMsgInfo,
                )
            }

        private fun TvmContext.loadBody(
            scope: TvmStepScopeManager,
            resolver: TvmTestStateResolver,
            ptr: ParsingState,
            quietBlock: (TvmState.() -> Unit)?,
        ): ValueOrDeadScope<Pair<UHeapRef?, UHeapRef>> {
            val bodyBit =
                sliceLoadIntTransaction(scope, ptr.slice, 1, quietBlock = quietBlock)?.unwrap(ptr)
                    ?: return scopeDied

            val bodyBitIsInlined = bodyBit eq zeroValue

            val (bodyCellOriginal, bodyCell) =
                if (resolver.eval(bodyBitIsInlined).isFalse) {
                    scope.assert(bodyBitIsInlined.not())
                        ?: return scopeDied

                    sliceLoadRefTransaction(scope, ptr.slice, quietBlock = quietBlock)
                        ?.unwrap(ptr)
                        ?.let { it to it }
                        ?: return scopeDied
                } else {
                    scope.assert(bodyBitIsInlined)
                        ?: return scopeDied

                    val bodyBuilder = scope.calcOnState { allocEmptyBuilder() }
                    // Note: the line below DOES NOT move pointers in ptr.
                    // It does not break anything, as we do not read from `ptr` after reading message body
                    // in this function, even though it is aesthetically unpleasant
                    builderStoreSliceTransaction(scope, bodyBuilder, ptr.slice)
                        ?: return scopeDied
                    ptr.slice = scope.calcOnState { allocSliceFromCell(allocEmptyCell()) }
                    val newBody = scope.builderToCell(bodyBuilder)

                    null to newBody
                }
            return (bodyCellOriginal to bodyCell).ok()
        }

        private fun TvmContext.loadStateInit(
            scope: TvmStepScopeManager,
            resolver: TvmTestStateResolver,
            ptr: ParsingState,
            quietBlock: (TvmState.() -> Unit)?,
        ): ValueOrDeadScope<UHeapRef?> {
            val stateInitBit =
                sliceLoadIntTransaction(scope, ptr.slice, 1, quietBlock = quietBlock)?.unwrap(ptr)
                    ?: return scopeDied
            val stateInitIsMissing = stateInitBit eq zeroValue

            val stateInitRef =
                if (resolver.eval(stateInitIsMissing).isTrue) {
                    scope.assert(stateInitIsMissing)
                        ?: return scopeDied

                    null
                } else {
                    scope.assert(stateInitIsMissing.not())
                        ?: return scopeDied

                    val stateInitInlineBit =
                        sliceLoadIntTransaction(scope, ptr.slice, 1, quietBlock = quietBlock)?.unwrap(ptr)
                            ?: return scopeDied

                    val stateInitIsInlined = stateInitInlineBit eq zeroValue

                    if (resolver.eval(stateInitIsInlined).isFalse) {
                        scope.assert(stateInitIsInlined.not())
                            ?: return scopeDied

                        sliceLoadRefTransaction(scope, ptr.slice, quietBlock = quietBlock)?.unwrap(ptr)
                            ?: return scopeDied
                    } else {
                        scope.assert(stateInitIsInlined)
                            ?: return scopeDied

                        // fixed_prefix_length:(Maybe (## 5)) special:(Maybe TickTock)
                        val stateInitPrefix =
                            sliceLoadIntTransaction(scope, ptr.slice, 2, quietBlock = quietBlock)?.unwrap(ptr)
                                ?: return scopeDied
                        scope.assert(stateInitPrefix eq zeroValue)
                            ?: return scopeDied

                        // code:(Maybe ^Cell)
                        loadMaybeRef(scope, ptr, resolver, quietBlock)
                            .getOrElse { return scopeDied }

                        // data:(Maybe ^Cell)
                        loadMaybeRef(scope, ptr, resolver, quietBlock)
                            .getOrElse { return scopeDied }

                        // library:(Maybe ^Cell)
                        loadMaybeRef(scope, ptr, resolver, quietBlock)
                            .getOrElse { return scopeDied }

                        null
                    }
                }
            return Ok(stateInitRef)
        }

        private fun loadMaybeRef(
            scope: TvmStepScopeManager,
            ptr: ParsingState,
            resolver: TvmTestStateResolver,
            quietBlock: (TvmState.() -> Unit)?,
        ): ValueOrDeadScope<UHeapRef?> =
            scope.doWithCtx {
                val maybeBit =
                    sliceLoadIntTransaction(scope, ptr.slice, 1, quietBlock = quietBlock)?.unwrap(ptr)
                        ?: return@doWithCtx scopeDied

                val refIsMissing = maybeBit eq zeroValue

                val value =
                    if (resolver.eval(refIsMissing).isTrue) {
                        scope.assert(refIsMissing)
                            ?: return@doWithCtx scopeDied
                        null
                    } else {
                        scope.assert(refIsMissing.not())
                            ?: return@doWithCtx scopeDied

                        sliceLoadRefTransaction(scope, ptr.slice)?.unwrap(ptr)
                            ?: return@doWithCtx scopeDied
                    }
                value.ok()
            }
    }
}

data class ParsingState(
    var slice: UHeapRef,
)

fun <T> Pair<UHeapRef, T>.unwrap(state: ParsingState): T {
    state.slice = first
    return second
}
