package org.usvm.machine.interpreter

import io.ksmt.utils.uncheckedCast
import org.ton.LinearDestinations
import org.ton.OpcodeToDestination
import org.ton.bytecode.ADDRESS_PARAMETER_IDX
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.isTrue
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.INT_BITS
import org.usvm.machine.TvmContext.Companion.NONE_ADDRESS_TAG
import org.usvm.machine.TvmContext.Companion.OP_BITS
import org.usvm.machine.TvmContext.Companion.STD_ADDRESS_TAG
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.bigIntValue
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmCommitedState
import org.usvm.machine.state.allocCellFromData
import org.usvm.machine.state.allocEmptyBuilder
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.builderStoreGramsTransaction
import org.usvm.machine.state.builderStoreIntTransaction
import org.usvm.machine.state.builderStoreSlice
import org.usvm.machine.state.builderStoreSliceTransaction
import org.usvm.machine.state.builderToCell
import org.usvm.machine.state.doWithCtx
import org.usvm.machine.state.getCellContractInfoParam
import org.usvm.machine.state.getSliceRemainingRefsCount
import org.usvm.machine.state.messages.OutMessage
import org.usvm.machine.state.messages.getMsgBodySlice
import org.usvm.machine.state.sliceLoadAddrTransaction
import org.usvm.machine.state.sliceLoadGramsTransaction
import org.usvm.machine.state.sliceLoadIntTransaction
import org.usvm.machine.state.sliceLoadRefTransaction
import org.usvm.machine.state.sliceMoveDataPtr
import org.usvm.machine.state.sliceMoveRefPtr
import org.usvm.machine.state.slicePreloadAddrLengthWithoutSetException
import org.usvm.machine.state.slicePreloadDataBits
import org.usvm.machine.state.slicePreloadExternalAddrLength
import org.usvm.machine.state.slicePreloadNextRef
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.sizeSort
import org.usvm.test.resolver.TvmTestStateResolver

class TvmTransactionInterpreter(
    val ctx: TvmContext,
) {
    data class ActionDestinationParsingResult(
        val unprocessedMessages: List<OutMessage>,
        val messagesForQueue: List<Pair<ContractId, OutMessage>>,
    )

    fun parseActionsToDestinations(
        scope: TvmStepScopeManager,
        commitedState: TvmCommitedState,
        restActions: TvmStepScopeManager.(ActionDestinationParsingResult) -> Unit,
    ) {
        val resolver = TvmTestStateResolver(ctx, scope.calcOnState { models.first() }, scope.calcOnState { this })

        val messages =
            parseOutMessages(scope, commitedState, resolver)
                ?: return

        if (messages.isEmpty()) {
            return ActionDestinationParsingResult(emptyList(), emptyList()).let {
                scope.restActions(it)
            }
        }

        if (!ctx.tvmOptions.intercontractOptions.isIntercontractEnabled) {
            return ActionDestinationParsingResult(messages, emptyList()).let {
                scope.restActions(it)
            }
        }

        val scheme =
            ctx.tvmOptions.intercontractOptions.communicationScheme
                ?: error("Communication scheme is not found")

        val contractId = scope.calcOnState { currentContract }
        val handlers =
            scheme[contractId]
                ?: error("Contract handlers are not found")

        val msgBody =
            scope.calcOnState { receivedMessage?.getMsgBodySlice() }
                ?: error("Unexpected null msg_body")

        val (handler, status) =
            chooseHandlerBasedOnOpcode(
                msgBody,
                handlers.inOpcodeToDestination,
                handlers.other,
                resolver,
                scope,
            )

        status ?: return

        if (handler == null) {
            return ActionDestinationParsingResult(messages, emptyList()).let {
                scope.restActions(it)
            }
        }

        return when (handler) {
            is LinearDestinations -> {
                check(handler.destinations.size == messages.size) {
                    "The number of actual messages is not equal to the number of destinations in the scheme: " +
                        "${messages.size} ${handler.destinations.size}"
                }

                val messagesForQueue = handler.destinations.zip(messages)
                ActionDestinationParsingResult(
                    unprocessedMessages = emptyList(),
                    messagesForQueue = messagesForQueue,
                ).let {
                    scope.restActions(it)
                }
            }

            is OpcodeToDestination -> {
                val destinationVariants =
                    messages.map {
                        val (result, innerStatus) =
                            chooseHandlerBasedOnOpcode(
                                it.msgBodySlice,
                                handler.outOpcodeToDestination,
                                handler.other,
                                resolver,
                                scope,
                            )

                        innerStatus ?: return

                        result ?: listOf(null)
                    }

                val combinations = mutableListOf<List<ContractId?>>()
                listAllCombinations(destinationVariants, combinations)

                val actions =
                    combinations.map { destinations ->
                        val newMessagesForQueue = mutableListOf<Pair<ContractId, OutMessage>>()
                        val newUnprocessedMessages = mutableListOf<OutMessage>()

                        destinations.zip(messages).forEach { (destination, message) ->
                            if (destination == null) {
                                newUnprocessedMessages.add(message)
                            } else {
                                newMessagesForQueue.add(destination to message)
                            }
                        }

                        TvmStepScopeManager.ActionOnCondition(
                            caseIsExceptional = false,
                            condition = ctx.trueExpr,
                            paramForDoForAllBlock =
                                ActionDestinationParsingResult(
                                    newUnprocessedMessages,
                                    newMessagesForQueue,
                                ),
                            action = {},
                        )
                    }

                scope.doWithConditions(actions, restActions)
            }
        }
    }

    private fun <T> listAllCombinations(
        variants: List<List<T>>,
        result: MutableList<List<T>>,
        curResult: MutableList<T> = mutableListOf(),
    ) {
        if (curResult.size == variants.size) {
            result.add(curResult.toList())
            return
        }
        val index = curResult.size
        variants[index].forEach { elem ->
            curResult.add(elem)
            listAllCombinations(variants, result, curResult)
            curResult.removeLast()
        }
    }

    private fun <T> chooseHandlerBasedOnOpcode(
        msgBodySlice: UHeapRef,
        variants: Map<String, T>,
        defaultVariant: T?,
        resolver: TvmTestStateResolver,
        scope: TvmStepScopeManager,
    ): Pair<T?, Unit?> =
        with(scope.ctx) {
            val msgBodyCell =
                scope.calcOnState {
                    memory.readField(
                        msgBodySlice,
                        TvmContext.sliceCellField,
                        addressSort,
                    )
                }

            val leftBits =
                scope.calcOnState {
                    fieldManagers.cellDataLengthFieldManager.readCellDataLength(this, msgBodyCell)
                }

            val hasOpcode = mkBvSignedGreaterOrEqualExpr(leftBits, mkSizeExpr(OP_BITS.toInt()))
            val handler =
                if (resolver.eval(hasOpcode).isTrue) {
                    scope.assert(hasOpcode)
                        ?: error("Unexpected solver result")

                    val inOpcode =
                        sliceLoadIntTransaction(scope, msgBodySlice, OP_BITS.toInt())?.second
                            ?: return null to null

                    val concreteOp = resolver.eval(inOpcode)
                    val concreteOpHex = concreteOp.bigIntValue().toString(16).padStart(TvmContext.OP_BYTES.toInt(), '0')
                    val concreteHandler = variants[concreteOpHex]

                    if (concreteHandler != null) {
                        scope.assert(inOpcode eq concreteOp)
                            ?: error("Unexpected solver result")

                        concreteHandler
                    } else {
                        defaultVariant
                    }
                } else {
                    defaultVariant
                }

            return handler to Unit
        }

    private fun parseOutMessages(
        scope: TvmStepScopeManager,
        commitedState: TvmCommitedState,
        resolver: TvmTestStateResolver,
    ): List<OutMessage>? =
        with(ctx) {
            val commitedActions = scope.calcOnState { commitedState.c5.value.value }

            val actions =
                extractActions(scope, commitedActions)
                    ?: return null

            val outMessages = mutableListOf<OutMessage>()

            for (action in actions) {
                val (actionBody, tag) =
                    sliceLoadIntTransaction(scope, action, 32)
                        ?: return null

                val isSendMsgAction = tag eq sendMsgActionTag.unsignedExtendToInteger()
                val isReserveAction = tag eq reserveActionTag.unsignedExtendToInteger()

                when {
                    resolver.eval(isReserveAction).isTrue -> {
                        scope.assert(isReserveAction)
                            ?: error("Unexpected solver result")

                        visitReserveAction(scope, actionBody)
                            ?: return null
                    }

                    resolver.eval(isSendMsgAction).isTrue -> {
                        scope.assert(isSendMsgAction)
                            ?: error("Unexpected solver result")

                        val msg =
                            visitSendMessageAction(scope, actionBody)
                                ?: return null

                        outMessages.add(msg)
                    }

                    else -> {
                        error("Unknown action in C5 register")
                    }
                }
            }

            return outMessages
        }

    private fun extractActions(
        scope: TvmStepScopeManager,
        actions: UHeapRef,
    ): List<UHeapRef>? =
        with(ctx) {
            var cur = actions
            val actionList = mutableListOf<UHeapRef>()

            while (true) {
                val slice = scope.calcOnState { allocSliceFromCell(cur) }
                val remainingRefs = scope.calcOnState { getSliceRemainingRefsCount(slice) }

                val isEnd =
                    scope.checkCondition(remainingRefs eq zeroSizeExpr)
                        ?: return null
                if (isEnd) {
                    // TODO check that `remainingBits` is also zero
                    break
                }

                val action =
                    sliceLoadRefTransaction(scope, slice)?.let {
                        cur = it.second
                        it.first
                    }
                        ?: return null
                actionList.add(action)

                if (actionList.size > TvmContext.MAX_ACTIONS) {
                    // TODO set error code
                    return null
                }
            }

            return actionList.reversed()
        }

    private fun visitSendMessageAction(
        scope: TvmStepScopeManager,
        slice: UHeapRef,
    ): OutMessage? =
        with(ctx) {
            val msg =
                scope.slicePreloadNextRef(slice)
                    ?: return null
            val msgSlice = scope.calcOnState { allocSliceFromCell(msg) }

            val ptr = ParsingState(msgSlice)
            val (msgFull, msgValue, destination) =
                parseCommonMsgInfoRelaxed(scope, ptr)
                    ?: return null
            parseStateInit(scope, ptr)
                ?: return null
            val bodySlice =
                parseBody(scope, ptr)
                    ?: return null

            OutMessage(msgValue, msgFull, bodySlice, destination)
        }

    private data class CommonMessageInfo(
        val msgFull: UHeapRef,
        val msgValue: UExpr<TvmInt257Sort>,
        val destAddrSlice: UHeapRef,
    )

    private fun parseCommonMsgInfoRelaxed(
        scope: TvmStepScopeManager,
        ptr: ParsingState,
    ): CommonMessageInfo? =
        with(ctx) {
            val msgFull = scope.calcOnState { allocEmptyBuilder() }

            val tag =
                sliceLoadIntTransaction(scope, ptr.slice, 1)?.second
                    ?: return@with null

            val isInternalCond = tag eq zeroValue
            val isInternal =
                scope.checkCondition(isInternalCond)
                    ?: return null

            if (isInternal) {
                // int_msg_info$0 ihr_disabled:Bool bounce:Bool bounced:Bool
                val flags =
                    sliceLoadIntTransaction(scope, ptr.slice, 4)?.unwrap(ptr)
                        ?: return@with null

                builderStoreIntTransaction(scope, msgFull, flags, mkSizeExpr(4))
                    ?: return@with null

                sliceSkipNoneOrStdAddr(scope, ptr.slice)?.unwrap(ptr)
                    ?: return@with null

                val addrCell =
                    scope.getCellContractInfoParam(ADDRESS_PARAMETER_IDX)
                        ?: return null
                val addrSlice = scope.calcOnState { allocSliceFromCell(addrCell) }
                builderStoreSliceTransaction(scope, msgFull, addrSlice)
                    ?: return null

                // dest:MsgAddressInt
                val destSlice =
                    sliceLoadAddrTransaction(scope, ptr.slice)?.unwrap(ptr)
                        ?: return@with null
                builderStoreSliceTransaction(scope, msgFull, destSlice)
                    ?: return null

                // value:CurrencyCollection
                // TODO: consider different modes
                val symbolicMsgValue =
                    sliceLoadGramsTransaction(scope, ptr.slice)?.unwrap(ptr)
                        ?: return@with null

                builderStoreGramsTransaction(scope, msgFull, symbolicMsgValue)
                    ?: return null

                // TODO possible cell overflow
                builderStoreSliceTransaction(scope, msgFull, ptr.slice)
                    ?: return null

                val extraCurrenciesBit =
                    sliceLoadIntTransaction(scope, ptr.slice, 1)?.unwrap(ptr)
                        ?: return@with null

                val extraCurrenciesEmptyConstraint = extraCurrenciesBit eq zeroValue
                val isExtraCurrenciesEmpty =
                    scope.checkCondition(extraCurrenciesEmptyConstraint)
                        ?: return null
                if (!isExtraCurrenciesEmpty) {
                    sliceLoadRefTransaction(scope, ptr.slice)?.unwrap(ptr)
                        ?: return@with null
                }

                // ihr_fee:Grams fwd_fee:Grams
                sliceLoadGramsTransaction(scope, ptr.slice)?.unwrap(ptr)
                    ?: return@with null
                sliceLoadGramsTransaction(scope, ptr.slice)?.unwrap(ptr)
                    ?: return@with null

                // created_lt:uint64 created_at:uint32
                sliceLoadIntTransaction(scope, ptr.slice, 64)?.unwrap(ptr)
                    ?: return@with null
                sliceLoadIntTransaction(scope, ptr.slice, 32)?.unwrap(ptr)
                    ?: return@with null

                return CommonMessageInfo(scope.builderToCell(msgFull), symbolicMsgValue, destSlice)
            }

            TODO("External messages are not supported")

            scope.builderStoreSlice(msgFull, msgFull, ptr.slice)
                ?: return null

            val externalTag =
                scope.slicePreloadDataBits(ptr.slice, 2)
                    ?: return null

            scope.fork(
                externalTag eq mkBv(value = 3, sizeBits = 2u),
                falseStateIsExceptional = true,
                // TODO set cell deserialization failure
                blockOnFalseState = throwStructuralCellUnderflowError,
            ) ?: return null

            // ext_out_msg_info$11
            scope.doWithState { sliceMoveDataPtr(ptr.slice, 2) }

            // src:MsgAddress
            val srcAddrLength =
                scope.slicePreloadAddrLengthWithoutSetException(ptr.slice)
                    ?: return null
            scope.doWithState { sliceMoveDataPtr(ptr.slice, srcAddrLength) }

            // dest:MsgAddressExt
            val destAddrLength =
                scope.slicePreloadExternalAddrLength(ptr.slice)
                    ?: return null
            val destAddr =
                scope.slicePreloadDataBits(ptr.slice, destAddrLength)
                    ?: return null
            scope.doWithState { sliceMoveDataPtr(ptr.slice, destAddrLength) }

            // created_lt:uint64 created_at:uint32
            scope.doWithState { sliceMoveDataPtr(ptr.slice, 64 + 32) }

            scope.allocCellFromData(destAddr, destAddrLength)

            null
        }

    private fun parseStateInit(
        scope: TvmStepScopeManager,
        ptr: ParsingState,
    ): Unit? =
        with(ctx) {
            // init:(Maybe (Either StateInit ^StateInit))
            val initMaybeBit =
                sliceLoadIntTransaction(scope, ptr.slice, 1)?.unwrap(ptr)
                    ?: return null
            val noStateInitConstraint = initMaybeBit eq zeroValue
            val noStateInit =
                scope.checkCondition(noStateInitConstraint)
                    ?: return null

            if (noStateInit) {
                return Unit
            }

            val eitherBit =
                sliceLoadIntTransaction(scope, ptr.slice, 1)?.unwrap(ptr)
                    ?: return null
            val isEitherRightCond = eitherBit eq oneValue
            val isEitherRight =
                scope.checkCondition(isEitherRightCond)
                    ?: return null

            if (isEitherRight) {
                sliceLoadRefTransaction(scope, ptr.slice)?.unwrap(ptr)
                    ?: return null
                return Unit
            }

            TODO("Raw state_init is not supported")

            // split_depth:(Maybe (## 5))
            val splitDepthMaybeBit =
                scope.slicePreloadDataBits(ptr.slice, 1)
                    ?: return null
            val splitDepthLen =
                mkIte(
                    splitDepthMaybeBit eq oneBit,
                    sixSizeExpr,
                    oneSizeExpr,
                )
            scope.doWithState { sliceMoveDataPtr(ptr.slice, splitDepthLen) }

            // special:(Maybe TickTock)
            val specialMaybeBit =
                scope.slicePreloadDataBits(ptr.slice, 1)
                    ?: return null
            val specialLen =
                mkIte(
                    specialMaybeBit eq oneBit,
                    threeSizeExpr,
                    oneSizeExpr,
                )
            scope.doWithState { sliceMoveDataPtr(ptr.slice, specialLen) }

            // code:(Maybe ^Cell) data:(Maybe ^Cell) library:(Maybe ^Cell)
            val codeMaybeBit =
                scope.slicePreloadDataBits(ptr.slice, 1)
                    ?: return null
            scope.doWithState { sliceMoveDataPtr(ptr.slice, 1) }
            val dataMaybeBit =
                scope.slicePreloadDataBits(ptr.slice, 1)
                    ?: return null
            scope.doWithState { sliceMoveDataPtr(ptr.slice, 1) }
            val libMaybeBit =
                scope.slicePreloadDataBits(ptr.slice, 1)
                    ?: return null
            scope.doWithState { sliceMoveDataPtr(ptr.slice, 1) }

            val refsToSkip =
                mkSizeAddExpr(
                    mkSizeAddExpr(
                        codeMaybeBit.zeroExtendToSort(sizeSort),
                        dataMaybeBit.zeroExtendToSort(sizeSort),
                    ),
                    libMaybeBit.zeroExtendToSort(sizeSort),
                )

            scope.doWithState { sliceMoveRefPtr(ptr.slice, refsToSkip) }

            return Unit
        }

    private fun parseBody(
        scope: TvmStepScopeManager,
        ptr: ParsingState,
    ): UHeapRef? =
        with(ctx) {
            //  body:(Either X ^X)
            val bodyEitherBit =
                sliceLoadIntTransaction(scope, ptr.slice, 1)?.unwrap(ptr)
                    ?: return null
            val isBodyLeft =
                scope.checkCondition(bodyEitherBit eq zeroValue)
                    ?: return null

            val body =
                if (isBodyLeft) {
                    val bodyBuilder = scope.calcOnState { allocEmptyBuilder() }
                    builderStoreSliceTransaction(scope, bodyBuilder, ptr.slice)
                        ?: return null
                    scope.builderToCell(bodyBuilder)
                } else {
                    sliceLoadRefTransaction(scope, ptr.slice)?.unwrap(ptr)
                        ?: return null
                }

            scope.calcOnState { allocSliceFromCell(body) }
        }

    private fun visitReserveAction(
        scope: TvmStepScopeManager,
        slice: UHeapRef,
    ): Unit? {
        // TODO no implementation, since we don't compute actions fees and balance

        return Unit
    }

    private fun sliceSkipNoneOrStdAddr(
        scope: TvmStepScopeManager,
        slice: UHeapRef,
    ): Pair<UHeapRef, Unit>? =
        scope.doWithCtx {
            val (afterTagSlice, tag) =
                sliceLoadIntTransaction(scope, slice, 2)
                    ?: return@doWithCtx null

            val noneTag = mkBv(NONE_ADDRESS_TAG, INT_BITS)
            val isTagNone =
                scope.checkCondition(tag eq noneTag.uncheckedCast())
                    ?: return@doWithCtx null

            if (isTagNone) {
                return@doWithCtx afterTagSlice to Unit
            }

            // TODO not fallback to old memory
            val stdTag = mkBv(STD_ADDRESS_TAG, INT_BITS)
            val isTagStd =
                scope.checkCondition(tag eq stdTag.uncheckedCast())
                    ?: return@doWithCtx null

            require(isTagStd) {
                "Only none and std source addresses are supported"
            }

            val (nextSlice, _) =
                sliceLoadAddrTransaction(scope, slice)
                    ?: return@doWithCtx null

            nextSlice to Unit
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

    private fun <T> Pair<UHeapRef, T>.unwrap(state: ParsingState): T {
        state.slice = first
        return second
    }

    private data class ParsingState(
        var slice: UHeapRef,
    )
}
