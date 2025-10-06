package org.usvm.machine.interpreter

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.ton.LinearDestinations
import org.ton.OpcodeToDestination
import org.ton.bytecode.ADDRESS_PARAMETER_IDX
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
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
import org.usvm.machine.splitHeadTail
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmCommitedState
import org.usvm.machine.state.allocEmptyBuilder
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.builderStoreGramsTransaction
import org.usvm.machine.state.builderStoreIntTransaction
import org.usvm.machine.state.builderStoreSliceTransaction
import org.usvm.machine.state.builderToCell
import org.usvm.machine.state.doWithCtx
import org.usvm.machine.state.getBalance
import org.usvm.machine.state.getCellContractInfoParam
import org.usvm.machine.state.getContractInfoParamOf
import org.usvm.machine.state.getInboundMessageValue
import org.usvm.machine.state.getSliceRemainingRefsCount
import org.usvm.machine.state.messages.MessageActionParseResult
import org.usvm.machine.state.messages.MessageAsStackArguments
import org.usvm.machine.state.messages.MessageMode
import org.usvm.machine.state.messages.getMsgBodySlice
import org.usvm.machine.state.sliceLoadAddrTransaction
import org.usvm.machine.state.sliceLoadGramsTransaction
import org.usvm.machine.state.sliceLoadIntTransaction
import org.usvm.machine.state.sliceLoadRefTransaction
import org.usvm.machine.state.slicePreloadInt
import org.usvm.machine.state.slicePreloadNextRef
import org.usvm.machine.state.slicesAreEqual
import org.usvm.mkSizeExpr
import org.usvm.test.resolver.TvmTestStateResolver

private typealias MsgHandlingPredicate = TvmTransactionInterpreter.MessageHandlingState.Ok.() -> UExpr<KBoolSort>
private typealias Transformation =
    TvmTransactionInterpreter.MessageHandlingState.Ok.() -> TvmTransactionInterpreter.MessageHandlingState

class TvmTransactionInterpreter(
    val ctx: TvmContext,
) {
    data class MessageWithMaybeReceiver(
        val receiver: ContractId?,
        val outMessage: MessageActionParseResult,
    )

    class ActionDestinationParsingResult(
        val orderedMessages: List<MessageWithMaybeReceiver>,
    ) {
        val unprocessedMessages: List<MessageActionParseResult>
            get() =
                orderedMessages.mapNotNull { (contractId, outMessage) ->
                    if (contractId == null) {
                        outMessage
                    } else {
                        null
                    }
                }
        val messagesForQueue: List<Pair<ContractId, MessageActionParseResult>>
            get() = orderedMessages.mapNotNull { (contractId, outMessage) -> contractId?.let { it to outMessage } }

        operator fun component1(): List<MessageActionParseResult> = unprocessedMessages

        operator fun component2(): List<Pair<ContractId, MessageActionParseResult>> = messagesForQueue
    }

    sealed interface MessageHandlingState {
        data class Ok(
            val ctx: TvmContext,
            var remainingBalance: UExpr<TvmInt257Sort>,
            var msgValue: UExpr<TvmInt257Sort>,
            var inboundMessageValue: UExpr<TvmInt257Sort>,
            var sentMessages: PersistentList<MessageWithMaybeReceiver> = persistentListOf(),
        ) : MessageHandlingState

        data class Failure(
            val exitCode: Int,
        ) : MessageHandlingState
    }

    private fun MessageHandlingState.applyTransform(f: Transformation): MessageHandlingState =
        when (this) {
            is MessageHandlingState.Failure -> this
            is MessageHandlingState.Ok -> f(this)
        }

    private fun MessageHandlingState.applyPredicate(
        ctx: TvmContext,
        f: MsgHandlingPredicate,
    ): KExpr<KBoolSort> =
        when (this) {
            is MessageHandlingState.Failure -> ctx.falseExpr
            is MessageHandlingState.Ok -> f(this)
        }

    data class CondTransform(
        val predicate: MsgHandlingPredicate,
        val transform: Transformation,
    )

    private fun asOnCopy(transformation: Transformation): Transformation =
        { ok: MessageHandlingState.Ok -> ok.copy().transformation() }

    fun handleMessages(
        scope: TvmStepScopeManager,
        messages: List<MessageWithMaybeReceiver>,
        restActions: TvmStepScopeManager.(ActionHandlingResult) -> Unit,
    ) {
        val messageHandlingState =
            scope.calcOnState {
                val balance = getBalance() ?: ctx.zeroValue
                val inboundMsgValue = getInboundMessageValue() ?: ctx.zeroValue
                MessageHandlingState.Ok(
                    ctx,
                    balance,
                    ctx.zeroValue,
                    inboundMsgValue,
                )
            }
        val compatibleRestActions: TvmStepScopeManager.(MessageHandlingState) -> Unit = {
            val arg =
                when (it) {
                    is MessageHandlingState.Failure -> ActionHandlingResult.Failure(it.exitCode)
                    is MessageHandlingState.Ok -> ActionHandlingResult.Success(it.remainingBalance, it.sentMessages)
                }
            this.restActions(arg)
        }
        scope.handleMessagesImpl(messages, messageHandlingState, compatibleRestActions)
    }

    private fun TvmStepScopeManager.handleMessagesImpl(
        messages: List<MessageWithMaybeReceiver>,
        currentMessageHandlingState: MessageHandlingState,
        restActions: TvmStepScopeManager.(MessageHandlingState) -> Unit,
    ): Unit =
        with(ctx) {
            val (head, tail) =
                messages.splitHeadTail() ?: run {
                    restActions(currentMessageHandlingState)
                    return
                }
            val mode = head.outMessage.sendMessageMode
            val sendRemainingValue = mode.hasBitSet(MessageMode.SEND_REMAINING_VALUE.toBv257())
            val sendRemainingBalance = mode.hasBitSet(MessageMode.SEND_REMAINING_BALANCE.toBv257())
            val messageValue = head.outMessage.content.msgValue
            ctx.handleSingleMessage(
                this@handleMessagesImpl,
                sendRemainingValue,
                sendRemainingBalance,
                zeroValue,
                messageValue,
                currentMessageHandlingState,
                head,
            ) { newCurrentState ->
                handleMessagesImpl(tail, newCurrentState, restActions)
            }
        }

    private fun TvmContext.handleSingleMessage(
        scope: TvmStepScopeManager,
        sendRemainingValue: UExpr<KBoolSort>,
        sendRemainingBalance: UExpr<KBoolSort>,
        computeFees: UExpr<TvmInt257Sort>,
        initMsgValue: UExpr<TvmInt257Sort>,
        currentState: MessageHandlingState,
        currentMessage: MessageWithMaybeReceiver,
        restActions: TvmStepScopeManager.(MessageHandlingState) -> Unit,
    ) {
        if (currentState is MessageHandlingState.Failure) {
            scope.restActions(currentState)
            return
        }
        val initialState = (currentState as MessageHandlingState.Ok).copy(msgValue = initMsgValue)
        val firstTransform: List<CondTransform> =
            listOf(
                CondTransform(
                    { sendRemainingValue and (msgValue bvUle computeFees) },
                    { MessageHandlingState.Failure(37) },
                ),
                CondTransform(
                    { sendRemainingValue and (msgValue bvUle computeFees).not() },
                    asOnCopy {
                        msgValue = msgValue bvAdd (inboundMessageValue bvSub computeFees)
                        this
                    },
                ),
                CondTransform(
                    { sendRemainingValue.not() },
                    asOnCopy { this },
                ),
            )
        val secondTransform: List<CondTransform> =
            listOf(
                CondTransform(
                    { (sendRemainingBalance and sendRemainingValue) },
                    { MessageHandlingState.Failure(37) },
                ),
                CondTransform(
                    { (sendRemainingBalance and sendRemainingValue.not()) },
                    asOnCopy {
                        msgValue = remainingBalance
                        remainingBalance = zeroValue
                        sentMessages = sentMessages.add(currentMessage)
                        this
                    },
                ),
                CondTransform(
                    { sendRemainingBalance.not() },
                    asOnCopy {
                        remainingBalance = remainingBalance bvSub msgValue
                        sentMessages = sentMessages.add(currentMessage)
                        this
                    },
                ),
            )
        val firstActions =
            firstTransform.map {
                TvmStepScopeManager.ActionOnCondition(
                    {},
                    false,
                    initialState.applyPredicate(ctx, it.predicate),
                    initialState.applyTransform(it.transform),
                )
            }
        scope.doWithConditions(
            firstActions,
            { nextState ->
                val secondActions =
                    secondTransform.map {
                        TvmStepScopeManager.ActionOnCondition(
                            {},
                            false,
                            nextState.applyPredicate(ctx, it.predicate),
                            nextState.applyTransform(it.transform),
                        )
                    }
                doWithConditions(secondActions) {
                    restActions(it)
                }
            },
        )
    }

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
            return ActionDestinationParsingResult(emptyList()).let {
                scope.restActions(it)
            }
        }

        if (!ctx.tvmOptions.intercontractOptions.isIntercontractEnabled) {
            return ActionDestinationParsingResult(messages.map { MessageWithMaybeReceiver(null, it) }).let {
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
            return ActionDestinationParsingResult(messages.map { MessageWithMaybeReceiver(null, it) }).let {
                scope.restActions(it)
            }
        }

        return when (handler) {
            is LinearDestinations -> {
                check(handler.destinations.size == messages.size) {
                    "The number of actual messages is not equal to the number of destinations in the scheme: " +
                        "${messages.size} ${handler.destinations.size}"
                }

                val messagesForQueue =
                    handler.destinations
                        .zip(messages)
                        .map { (receiver, message) -> MessageWithMaybeReceiver(receiver, message) }
                ActionDestinationParsingResult(messagesForQueue).let { scope.restActions(it) }
            }

            is OpcodeToDestination -> {
                val destinationVariants =
                    messages.map {
                        val (result, innerStatus) =
                            chooseHandlerBasedOnOpcode(
                                it.content.msgBodySlice,
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
                        val parsedMessages =
                            destinations.zip(messages) { dest, msg -> MessageWithMaybeReceiver(dest, msg) }

                        TvmStepScopeManager.ActionOnCondition(
                            caseIsExceptional = false,
                            condition = ctx.trueExpr,
                            paramForDoForAllBlock = ActionDestinationParsingResult(parsedMessages),
                            action = {},
                        )
                    }

                scope.doWithConditions(actions) { param ->
                    assertCorrectAddresses(this, param.messagesForQueue)
                    restActions(param)
                }
            }
        }
    }

    private fun assertCorrectAddresses(
        scope: TvmStepScopeManager,
        newMessagesForQueue: List<Pair<ContractId, MessageActionParseResult>>,
    ): Unit? =
        with(scope.ctx) {
            val constraint =
                newMessagesForQueue.fold(trueExpr as UBoolExpr) { acc, (destinationContract, message) ->
                    val destinationContractAddress =
                        scope.calcOnState {
                            (
                                getContractInfoParamOf(
                                    ADDRESS_PARAMETER_IDX,
                                    destinationContract,
                                ).cellValue as? UConcreteHeapRef
                            )?.let { allocSliceFromCell(it) }
                                ?: error("Cannot extract contract address")
                        }

                    val equality =
                        scope.slicesAreEqual(
                            destinationContractAddress,
                            message.content.destAddrSlice
                                ?: error("Contract address was not extracted during the paring"),
                        )
                            ?: return null

                    acc and equality
                }

            return scope.assert(constraint)
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
    ): List<MessageActionParseResult>? =
        with(ctx) {
            val commitedActions = scope.calcOnState { commitedState.c5.value.value }

            val actions =
                extractActions(scope, commitedActions)
                    ?: return null

            val outMessages = mutableListOf<MessageActionParseResult>()

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

                        visitReserveAction()
                    }

                    resolver.eval(isSendMsgAction).isTrue -> {
                        scope.assert(isSendMsgAction)
                            ?: error("Unexpected solver result")

                        val msg =
                            parseAndPreprocessMessageAction(scope, actionBody)
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

    private fun parseAndPreprocessMessageAction(
        scope: TvmStepScopeManager,
        slice: UHeapRef,
    ): MessageActionParseResult? =
        with(ctx) {
            val sendMsgMode =
                scope.slicePreloadInt(slice, eightSizeExpr, false)
                    ?: return null
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

            MessageActionParseResult(MessageAsStackArguments(msgValue, msgFull, bodySlice, destination), sendMsgMode)
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
            } else {
                TODO("External messages are not supported")
            }
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
            } else {
                TODO("Raw state_init is not supported")
            }
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

    private fun visitReserveAction() {
        // TODO no implementation, since we don't compute actions fees and balance
        return
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
