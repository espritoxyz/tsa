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
import org.usvm.logger
import org.usvm.machine.Int257Expr
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.INT_BITS
import org.usvm.machine.TvmContext.Companion.NONE_ADDRESS_TAG
import org.usvm.machine.TvmContext.Companion.STD_ADDRESS_TAG
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.allocCellFromBuilder
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
import org.usvm.machine.state.sliceLoadAddrTlbNoFork
import org.usvm.machine.state.sliceLoadGramsTlbNoFork
import org.usvm.machine.state.sliceLoadIntTlbNoFork
import org.usvm.machine.state.sliceLoadRefTransaction
import org.usvm.machine.types.CellGeneralRef
import org.usvm.machine.types.CellRef
import org.usvm.machine.types.ConcreteCellRef
import org.usvm.machine.types.SliceRef
import org.usvm.machine.types.asCellRef
import org.usvm.machine.types.asSliceRef
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
                            sliceLoadIntTlbNoFork(scope, ptr.slice, 1, quietBlock = quietBlock)?.unwrap(ptr)
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
                    sliceLoadAddrTlbNoFork(scope, ptr.slice, quietBlock)?.unwrap(ptr)
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
                    sliceLoadGramsTlbNoFork(scope, ptr.slice, quietBlock = quietBlock)?.unwrap(ptr)
                        ?: return@with null

                val tlbExtraCurrenciesBit =
                    sliceLoadIntTlbNoFork(scope, ptr.slice, 1, quietBlock = quietBlock)?.unwrap(ptr)
                        ?: return@with null

                val extraCurrenciesEmptyConstraint = tlbExtraCurrenciesBit eq zeroValue
                scope.assert(extraCurrenciesEmptyConstraint)
                    ?: return@with null

                // extra_currency_bit
                val ihrFee =
                    sliceLoadGramsTlbNoFork(scope, ptr.slice, quietBlock)?.unwrap(ptr)
                        ?: return@with null

                // fwd_fee:Grams
                val fwdFee =
                    sliceLoadGramsTlbNoFork(scope, ptr.slice, quietBlock)?.unwrap(ptr)
                        ?: return@with null

                // created_lt:uint64 created_at:uint32
                val createdLt =
                    sliceLoadIntTlbNoFork(scope, ptr.slice, 64, quietBlock = quietBlock)?.unwrap(ptr)
                        ?: return@with null
                val createdAt =
                    sliceLoadIntTlbNoFork(scope, ptr.slice, 32)?.unwrap(ptr)
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
                    sliceLoadIntTlbNoFork(scope, slice, 2)
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
                    sliceLoadAddrTlbNoFork(scope, slice, quietBlock = quietBlock)
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

sealed interface TlbBody {
    data class Inline(
        val slice: SliceRef,
    ) : TlbBody

    data class OutOfLine(
        val originalRef: CellRef,
        val originalRefAsSlice: SliceRef,
    ) : TlbBody
}

fun TlbBody.originalRef(): CellRef? =
    when (this) {
        is TlbBody.Inline -> null
        is TlbBody.OutOfLine -> originalRef
    }

fun TlbBody.asSlice(): SliceRef =
    when (this) {
        is TlbBody.Inline -> slice
        is TlbBody.OutOfLine -> originalRefAsSlice
    }

/**
 * Isomorphic to `Maybe<Either<StateInit, ^StateInit>>`
 */
sealed interface TlbStateInit {
    data object None : TlbStateInit

    /**
     * See block.tlb:StateInit in TON monorepo.
     * We assume that `fixed_prefix_length=special=Nothing`
     */
    data class Inline(
        val code: CellRef?,
        val data: CellRef?,
        val library: CellRef?,
    ) : TlbStateInit

    data class OutOfLine(
        val originalRef: CellRef,
    ) : TlbStateInit
}

fun TlbStateInit.asCellRefUnsafe(): CellGeneralRef<UHeapRef>? =
    when (this) {
        TlbStateInit.None -> null

        // TODO proper forward fees for inline stateinit
        is TlbStateInit.Inline -> null

        is TlbStateInit.OutOfLine -> originalRef
    }

/**
 * Contains the part of the message after the CommonMsgInfo
 */
sealed interface MessageAfterCommonMsgInfo {
    /**
     * Is the original cell where the body was contained (null if was an inlined slice)
     */
    val body: TlbBody
    val stateInit: TlbStateInit

    /**
     * Contains empty state init and a body as an out-of-line cell
     */
    data class ManuallyConstructed(
        // init is assumed to be (Maybe (Either StateInit ^StateInit)).nothing (1 bit of zero)
        override val body: TlbBody.OutOfLine,
    ) : MessageAfterCommonMsgInfo {
        override val stateInit: TlbStateInit = TlbStateInit.None
    }

    /**
     * @param tailSlice embodies the whole slice of the message that follows the CommonMsgInfo
     */
    data class ConstructedBySomeContract(
        val tailSlice: UHeapRef,
        override val stateInit: TlbStateInit,
        override val body: TlbBody,
    ) : MessageAfterCommonMsgInfo
}

fun MessageAfterCommonMsgInfo.bodySlice(): SliceRef =
    when (this) {
        is MessageAfterCommonMsgInfo.ManuallyConstructed -> this.body.asSlice()
        is MessageAfterCommonMsgInfo.ConstructedBySomeContract -> this.body.asSlice()
    }

data class ConstructedMessageCells(
    val messageBody: SliceRef,
    val fullMessage: ConcreteCellRef,
)

/**
 * Use this class with caution, as the structure it contains might cause cell overflow
 */
data class TlbInternalMessageContent(
    val commonMessageInfo: TlbCommonMessageInfo,
    val messageAfterCommonMsgInfo: MessageAfterCommonMsgInfo,
) {
    val bodyOriginalRef: UHeapRef?
        get() = messageAfterCommonMsgInfo.body.originalRef()?.value
    val stateInit = messageAfterCommonMsgInfo.stateInit

    private fun UConcreteHeapRef.storeUint(
        scope: TvmStepScopeManager,
        value: Int257Expr,
        sizeBits: Int = 1,
    ): Unit? =
        builderStoreIntTlb(
            scope = scope,
            builder = this,
            updatedBuilder = this,
            sizeBits = with(scope.ctx) { sizeBits.toSizeSort() },
            value = value,
            isSigned = false,
            endian = Endian.BigEndian,
        )

    private fun UConcreteHeapRef.storeMaybeRefNoOverflowChecks(
        scope: TvmStepScopeManager,
        value: CellRef?,
    ): Unit? {
        if (value == null) {
            storeNothingBit(scope)
                ?: return null
        } else {
            storeJustBit(scope)
                ?: return null
            scope.calcOnState {
                builderStoreNextRefNoOverflowCheck(this@storeMaybeRefNoOverflowChecks, value.value)
            }
        }
        return Unit
    }

    private fun UConcreteHeapRef.storeJustBit(scope: TvmStepScopeManager) = storeUint(scope, scope.ctx.oneValue)

    private fun UConcreteHeapRef.storeNothingBit(scope: TvmStepScopeManager) = storeUint(scope, scope.ctx.zeroValue)

    /**
     * The inline value in this context.
     */
    private fun UConcreteHeapRef.storeEitherLeftBit(scope: TvmStepScopeManager) = storeUint(scope, scope.ctx.zeroValue)

    /**
     * The out-of-line value in this context.
     */
    private fun UConcreteHeapRef.storeEitherRightBit(scope: TvmStepScopeManager) = storeUint(scope, scope.ctx.oneValue)

    /**
     * @return `null` iff the message failed to construct due to an overflow
     */
    fun constructMessageCellFromContent(
        scope: TvmStepScopeManager,
        quietBlock: (TvmState.() -> Unit)? = null,
        restActions: TvmStepScopeManager.(ConstructedMessageCells) -> Unit,
    ): Unit? {
        val state = scope.calcOnState { this }
        return with(state.ctx) {
            val resultBuilder = state.allocEmptyBuilder()

            val commonMessageInfo = this@TlbInternalMessageContent.commonMessageInfo
            for (flag in commonMessageInfo.flags.asFlagsList()) {
                resultBuilder.storeUint(scope, flag)
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
            resultBuilder.storeUint(scope, zeroValue)
                ?: error("Cannot store extra currency collection")

            // ihr_fee:Grams
            builderStoreGramsTlb(scope, resultBuilder, resultBuilder, commonMessageInfo.ihrFee)
                ?: error("Cannot store ihr fee")

            // fwd_fee:Gram
            builderStoreGramsTlb(scope, resultBuilder, resultBuilder, commonMessageInfo.fwdFee)
                ?: error("Cannot store fwd fee")

            // created_lt:uint64
            resultBuilder.storeUint(scope, commonMessageInfo.createdLt, sizeBits = 64)
                ?: error("Cannot store created_lt")

            // created_at:uint32
            resultBuilder.storeUint(scope, commonMessageInfo.createdAt, sizeBits = 32)
                ?: error("Cannot store created_at")

            val tail = this@TlbInternalMessageContent.messageAfterCommonMsgInfo
            when (tail) {
                is MessageAfterCommonMsgInfo.ManuallyConstructed -> {
                    resultBuilder.storeNothingBit(scope)
                        ?: error("cannot store init")

                    // body:(Either X ^X).right
                    // set prefix of Either.right
                    resultBuilder.storeEitherRightBit(scope)

                    scope.doWithState {
                        builderStoreNextRefNoOverflowCheck(resultBuilder, tail.body.originalRef.value)
                    }
                }

                is MessageAfterCommonMsgInfo.ConstructedBySomeContract -> {
                    when (stateInit) {
                        TlbStateInit.None -> {
                            resultBuilder.storeNothingBit(scope)
                                ?: error("Cannot store init")
                        }

                        is TlbStateInit.Inline -> {
                            resultBuilder.storeJustBit(scope)
                                ?: error("cannot store init")
                            resultBuilder.storeEitherLeftBit(scope)
                                ?: error("cannot store init")
                            // fixed_prefix_length=special=Nothing
                            resultBuilder.storeUint(scope, zeroValue, 2)
                                ?: error("cannot store init")
                            resultBuilder.storeMaybeRefNoOverflowChecks(
                                scope,
                                stateInit.code,
                            )
                                ?: error("cannot store init")
                            resultBuilder.storeMaybeRefNoOverflowChecks(
                                scope,
                                stateInit.data,
                            )
                                ?: error("cannot store init")

                            resultBuilder.storeMaybeRefNoOverflowChecks(
                                scope,
                                stateInit.library,
                            )
                                ?: error("cannot store init")
                        }

                        is TlbStateInit.OutOfLine -> {
                            // Maybe.just
                            resultBuilder.storeUint(scope, oneValue)
                                ?: error("Cannot store init")
                            // Either.right
                            resultBuilder.storeUint(scope, oneValue)
                                ?: error("Cannot store init")
                            scope.calcOnState {
                                builderStoreNextRefNoOverflowCheck(resultBuilder, stateInit.originalRef.value)
                            }
                        }
                    }
                    when (val body = tail.body) {
                        is TlbBody.Inline -> {
                            resultBuilder.storeUint(scope, zeroValue)
                            builderStoreSliceTlb(
                                scope,
                                resultBuilder,
                                resultBuilder,
                                body.slice.value,
                                quietBlock,
                            ) ?: return@with null
                        }

                        is TlbBody.OutOfLine -> {
                            resultBuilder.storeUint(scope, oneValue)
                            scope.doWithState {
                                // actually, there might be an overflow (when stateinit is stored inline)
                                // careful here!
                                builderStoreNextRefNoOverflowCheck(resultBuilder, tail.body.originalRef.value)
                            }
                        }
                    }
                }
            }

            val bodySlice = tail.body.asSlice()
            val fullMessage = state.builderToCell(resultBuilder).asCellRef()
            scope.restActions(ConstructedMessageCells(messageBody = bodySlice, fullMessage = fullMessage))
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

                val body =
                    loadBody(scope, resolver, ptr, quietBlock)
                        .getOrElse { return@with null }

                val messageAfterCommonMsgInfo =
                    MessageAfterCommonMsgInfo.ConstructedBySomeContract(
                        tailSlice,
                        stateInitRef,
                        body,
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
        ): ValueOrDeadScope<TlbBody> {
            val bodyBit =
                sliceLoadIntTlbNoFork(scope, ptr.slice, 1, quietBlock = quietBlock)?.unwrap(ptr)
                    ?: return scopeDied

            val bodyBitIsInlined = bodyBit eq zeroValue

            val body =
                if (resolver.eval(bodyBitIsInlined).isFalse) {
                    scope.assert(bodyBitIsInlined.not())
                        ?: return scopeDied

                    sliceLoadRefTransaction(scope, ptr.slice, quietBlock = quietBlock)
                        ?.unwrap(ptr)
                        ?.let { bodyRef ->
                            val slice = scope.calcOnState { allocSliceFromCell(bodyRef) }
                            TlbBody.OutOfLine(bodyRef, slice)
                        }
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
                    val newBody =
                        scope
                            .calcOnState { allocSliceFromCell(allocCellFromBuilder(bodyBuilder)) }
                            .asSliceRef()

                    TlbBody.Inline(newBody)
                }
            return body.ok()
        }

        private fun TvmContext.loadStateInit(
            scope: TvmStepScopeManager,
            resolver: TvmTestStateResolver,
            ptr: ParsingState,
            quietBlock: (TvmState.() -> Unit)?,
        ): ValueOrDeadScope<TlbStateInit> {
            val stateInitBit =
                sliceLoadIntTlbNoFork(scope, ptr.slice, 1, quietBlock = quietBlock)?.unwrap(ptr)
                    ?: return scopeDied
            val stateInitIsMissing = stateInitBit eq zeroValue

            val tlbStateInit =
                if (resolver.eval(stateInitIsMissing).isTrue) {
                    scope.assert(stateInitIsMissing)
                        ?: return scopeDied

                    TlbStateInit.None
                } else {
                    scope.assert(stateInitIsMissing.not())
                        ?: return scopeDied

                    val stateInitInlineBit =
                        sliceLoadIntTlbNoFork(scope, ptr.slice, 1, quietBlock = quietBlock)?.unwrap(ptr)
                            ?: return scopeDied

                    val stateInitIsInlined = stateInitInlineBit eq zeroValue

                    if (resolver.eval(stateInitIsInlined).isFalse) {
                        scope.assert(stateInitIsInlined.not())
                            ?: return scopeDied

                        val ref =
                            sliceLoadRefTransaction(scope, ptr.slice, quietBlock = quietBlock)?.unwrap(ptr)
                                ?: return scopeDied
                        TlbStateInit.OutOfLine(ref)
                    } else {
                        scope.assert(stateInitIsInlined)
                            ?: return scopeDied

                        // fixed_prefix_length:(Maybe (## 5)) special:(Maybe TickTock)
                        val stateInitPrefix =
                            sliceLoadIntTlbNoFork(scope, ptr.slice, 2, quietBlock = quietBlock)?.unwrap(ptr)
                                ?: return scopeDied
                        scope.assert(stateInitPrefix eq zeroValue)
                            ?: run {
                                logger.warn("Only StateInits with empty fixed_prefix_length and special are supported")
                                return scopeDied
                            }

                        // code:(Maybe ^Cell)
                        val code =
                            loadMaybeRef(scope, ptr, resolver, quietBlock)
                                .getOrElse { return scopeDied }

                        // data:(Maybe ^Cell)
                        val data =
                            loadMaybeRef(scope, ptr, resolver, quietBlock)
                                .getOrElse { return scopeDied }

                        // library:(Maybe ^Cell)
                        val library =
                            loadMaybeRef(scope, ptr, resolver, quietBlock)
                                .getOrElse { return scopeDied }

                        TlbStateInit.Inline(code, data, library)
                    }
                }
            return Ok(tlbStateInit)
        }

        private fun loadMaybeRef(
            scope: TvmStepScopeManager,
            ptr: ParsingState,
            resolver: TvmTestStateResolver,
            quietBlock: (TvmState.() -> Unit)?,
        ): ValueOrDeadScope<CellRef?> =
            scope.doWithCtx {
                val maybeBit =
                    sliceLoadIntTlbNoFork(scope, ptr.slice, 1, quietBlock = quietBlock)?.unwrap(ptr)
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
