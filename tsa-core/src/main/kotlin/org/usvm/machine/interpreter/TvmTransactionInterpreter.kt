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
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.readField
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.Int257Expr
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
import org.usvm.machine.state.InsufficientFunds
import org.usvm.machine.state.TvmCommitedState
import org.usvm.machine.state.TvmMethodResult
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
import org.usvm.machine.state.makeCellToSliceTransaction
import org.usvm.machine.state.messages.FwdFeeInfo
import org.usvm.machine.state.messages.MessageActionParseResult
import org.usvm.machine.state.messages.MessageAsStackArguments
import org.usvm.machine.state.messages.MessageMode
import org.usvm.machine.state.messages.getMsgBodySlice
import org.usvm.machine.state.sliceLoadAddrTransaction
import org.usvm.machine.state.sliceLoadGramsTransaction
import org.usvm.machine.state.sliceLoadIntTransaction
import org.usvm.machine.state.sliceLoadRefTransaction
import org.usvm.machine.state.slicePreloadNextRef
import org.usvm.machine.state.slicesAreEqual
import org.usvm.mkSizeExpr
import org.usvm.test.resolver.TvmTestStateResolver

private typealias MsgHandlingPredicate = TvmTransactionInterpreter.MessageHandlingState.Ok.() -> UExpr<KBoolSort>
private typealias Transformation =
    TvmTransactionInterpreter.MessageHandlingState.Ok.() -> TvmTransactionInterpreter.MessageHandlingState

private typealias MutableTransformation =
    TvmTransactionInterpreter.MessageHandlingState.OkBuilder.() -> TvmTransactionInterpreter.MessageHandlingState

const val MSG_FWD_FEE_UPPER_BOUND = 100

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
        /**
         * @param remainingInboundMessageValue represents the value we received with the inbound message that we have
         * not yet spent.
         */
        data class OkBuilder(
            val ctx: TvmContext,
            var currentContractBalance: UExpr<TvmInt257Sort>,
            var messageValue: UExpr<TvmInt257Sort>,
            var messageValueBrutto: UExpr<TvmInt257Sort>,
            var remainingInboundMessageValue: UExpr<TvmInt257Sort>,
            var sentMessages: PersistentList<MessageWithMaybeReceiver> = persistentListOf(),
        ) {
            fun build() =
                Ok(
                    ctx,
                    currentContractBalance,
                    messageValue,
                    messageValueBrutto,
                    remainingInboundMessageValue,
                    sentMessages,
                )
        }

        data class Ok(
            val ctx: TvmContext,
            val remainingBalance: UExpr<TvmInt257Sort>,
            val messageValue: UExpr<TvmInt257Sort>,
            val messageValueBrutto: UExpr<TvmInt257Sort>,
            val remainingInboundMessageValue: UExpr<TvmInt257Sort>,
            val sentMessages: PersistentList<MessageWithMaybeReceiver> = persistentListOf(),
        ) : MessageHandlingState {
            fun toBuilder() =
                OkBuilder(
                    ctx,
                    remainingBalance,
                    messageValue,
                    messageValueBrutto,
                    remainingInboundMessageValue,
                    sentMessages,
                )
        }

        data class Failure(
            val exit: TvmMethodResult.TvmErrorExit,
        ) : MessageHandlingState

        companion object {
            fun insufficientFundsError(contractId: ContractId) = Failure(InsufficientFunds(contractId))
        }
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
            is MessageHandlingState.Failure -> ctx.trueExpr
            is MessageHandlingState.Ok -> f(this)
        }

    data class CondTransform(
        val predicate: MsgHandlingPredicate,
        val transform: Transformation,
    )

    private fun asOnCopy(transformation: MutableTransformation): Transformation =
        { ok: MessageHandlingState.Ok -> ok.copy().toBuilder().transformation() }

    fun handleMessages(
        scope: TvmStepScopeManager,
        messages: List<MessageWithMaybeReceiver>,
        restActions: TvmStepScopeManager.(ActionHandlingResult) -> Unit,
    ) {
        val messageHandlingState =
            scope.calcOnState {
                val balance = getBalance() ?: error("Balance not set")
                val inboundMsgValue = getInboundMessageValue() ?: error("Inbound message not set")
                MessageHandlingState.Ok(
                    ctx,
                    balance,
                    ctx.zeroValue, // not important, will be reassigned at each message
                    ctx.zeroValue,
                    inboundMsgValue,
                )
            }
        val compatibleRestActions: TvmStepScopeManager.(MessageHandlingState) -> Unit = {
            val arg =
                when (it) {
                    is MessageHandlingState.Failure ->
                        ActionHandlingResult.Failure(
                            InsufficientFunds(calcOnState { currentContract }),
                        )

                    is MessageHandlingState.Ok -> ActionHandlingResult.Success(it.remainingBalance, it.sentMessages)
                }
            this.restActions(arg)
        }
        scope.handleMessagesImpl(messages, messageHandlingState, compatibleRestActions)
    }

    /**
     * TODO calculate properly instead of mocking
     */
    private fun TvmStepScopeManager.computeMessageForwardFees(
        @Suppress("Unused") outMessage: MessageActionParseResult,
    ): Int257Expr {
        val fwdFees = calcOnState { makeSymbolicPrimitive(ctx.int257sort) }
        with(ctx) {
            assert((zeroValue bvUlt fwdFees) and (fwdFees bvUle MSG_FWD_FEE_UPPER_BOUND.toBv257()))
                ?: error("unreachable")
        }
        return fwdFees
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
            val sendRemainingValue = mode.hasBitSet(MessageMode.SEND_REMAINING_VALUE_BIT)
            val sendRemainingBalance = mode.hasBitSet(MessageMode.SEND_REMAINING_BALANCE_BIT)
            val sendFwdFeesSeparately = mode.hasBitSet(MessageMode.SEND_FEES_SEPARATELY)
            val messageValue = head.outMessage.content.msgValue
            ctx.handleSingleMessage(
                scope = this@handleMessagesImpl,
                sendRemainingValue = sendRemainingValue,
                sendRemainingBalance = sendRemainingBalance,
                sendFwdFeesSeparately = sendFwdFeesSeparately,
                computeFees = zeroValue,
                msgFwdFees = computeMessageForwardFees(head.outMessage),
                initMsgValue = messageValue,
                currentState = currentMessageHandlingState,
                currentMessage = head,
            ) { newCurrentState ->
                handleMessagesImpl(tail, newCurrentState, restActions)
            }
        }

    private fun MessageWithMaybeReceiver.withSetMessageValue(messageValue: Int257Expr): MessageWithMaybeReceiver =
        copy(outMessage = outMessage.copy(content = outMessage.content.copy(msgValue = messageValue)))

    /**
     *
     * The message handling is based on the flow in `Transaction::try_action_send_msg` defined in
     * `crypto/block/transaction.cpp` file relative to the root of the TON monorepo, tag `v2025.7`.
     *
     * The code that it represents:
     * ```
     * val msgFees = calculateMessageFees()
     * var initialMessageValue = message.getValue()
     *
     * val req = initialMessageValue // req = this.messageValue
     * payFeesSeparately = payFeesSeparately && !sendRemainingBalance
     *
     * if (sendRemainingBalance) {
     *     remainingInboundMsgValue = 0
     *     req = currentBalance
     * } else if (payAllValue) {
     *     remainingInboundMsgValue = 0
     *     if (remainingInboundMsgValue >= computeFees)
     *         req = messageValue - computeFees
     *     else
     *         return Err(37)
     * } else {
     *     // do nothing
     * }
     *
     * if (!payFeesSeparately) {
     *     if (req < msgFees) {
     *         return Err(37)
     *     } else {
     *        reqBrutto = req // reqBrutto is value subtracted from balance
     *        req = req - msgFees
     *     }
     * } else {
     *     reqBrutto = req + msgFees
     * }
     *
     * if (balance >= reqBrutto) {
     *     balance -= reqBrutto
     *     // send message
     * } else {
     *     return Err(37)
     * }
     *
     * ```
     *
     * @return list of transformations that are applied to the initial state during the message handling
     *
     */
    private fun TvmContext.createMessageHandlingTransformations(
        sendRemainingValue: UBoolExpr,
        sendRemainingBalance: UBoolExpr,
        sendFwdFeesSeparately: UBoolExpr,
        computeFees: Int257Expr,
        msgFwdFees: Int257Expr,
        currentMessage: MessageWithMaybeReceiver,
        currentContractId: ContractId,
    ): List<List<CondTransform>> {
        val payFeesSeparately = sendFwdFeesSeparately and sendRemainingBalance.not()
        val firstTransform: List<CondTransform> =
            listOf(
                CondTransform(
                    { sendRemainingBalance },
                    asOnCopy {
                        remainingInboundMessageValue = zeroValue
                        messageValue = currentContractBalance
                        build()
                    },
                ),
                CondTransform(
                    {
                        sendRemainingBalance.not() and sendRemainingValue and
                            (remainingInboundMessageValue bvUge computeFees)
                    },
                    asOnCopy {
                        messageValue = remainingInboundMessageValue bvSub computeFees
                        remainingInboundMessageValue = zeroValue
                        build()
                    },
                ),
                CondTransform(
                    {
                        sendRemainingBalance.not() and sendRemainingValue and
                            (remainingInboundMessageValue bvUge computeFees).not()
                    },
                    asOnCopy {
                        MessageHandlingState.insufficientFundsError(currentContractId)
                    },
                ),
                CondTransform(
                    {
                        sendRemainingBalance.not() and sendRemainingValue.not()
                    },
                    asOnCopy { build() },
                ),
            )
        val secondTransform: List<CondTransform> =
            listOf(
                CondTransform(
                    { payFeesSeparately },
                    asOnCopy {
                        messageValueBrutto = messageValue bvAdd msgFwdFees
                        build()
                    },
                ),
                CondTransform(
                    { payFeesSeparately.not() and (this.messageValue bvUge msgFwdFees) },
                    asOnCopy {
                        messageValueBrutto = messageValue
                        messageValue = messageValue bvSub msgFwdFees
                        build()
                    },
                ),
                CondTransform(
                    { payFeesSeparately.not() and (this.messageValue bvUge msgFwdFees).not() },
                    asOnCopy {
                        MessageHandlingState.insufficientFundsError(currentContractId)
                    },
                ),
            )
        val thirdTransform: List<CondTransform> =
            listOf(
                CondTransform(
                    { remainingBalance bvUge messageValueBrutto },
                    asOnCopy {
                        currentContractBalance = currentContractBalance bvSub messageValueBrutto
                        sentMessages = sentMessages.add(currentMessage.withSetMessageValue(messageValue))
                        build()
                    },
                ),
                CondTransform(
                    { (remainingBalance bvUge messageValueBrutto).not() },
                    asOnCopy {
                        MessageHandlingState.insufficientFundsError(currentContractId)
                    },
                ),
            )
        val transformations = listOf(firstTransform, secondTransform, thirdTransform)
        return transformations
    }

    private fun applyConsecutiveTransformations(
        scope: TvmStepScopeManager,
        currentState: MessageHandlingState,
        transformations: List<List<CondTransform>>,
        restActions: TvmStepScopeManager.(MessageHandlingState) -> Unit,
    ) {
        if (currentState is MessageHandlingState.Failure) {
            scope.restActions(currentState)
            return
        }
        val (head, tail) =
            transformations.splitHeadTail() ?: run {
                scope.restActions(currentState)
                return
            }
        val actions =
            head.map {
                TvmStepScopeManager.ActionOnCondition(
                    {},
                    false,
                    currentState.applyPredicate(ctx, it.predicate),
                    currentState.applyTransform(it.transform),
                )
            }
        scope.doWithConditions(actions) { updatedState ->
            applyConsecutiveTransformations(this, updatedState, tail, restActions)
        }
    }

    private fun TvmContext.handleSingleMessage(
        scope: TvmStepScopeManager,
        sendRemainingValue: UBoolExpr,
        sendRemainingBalance: UBoolExpr,
        sendFwdFeesSeparately: UBoolExpr,
        computeFees: Int257Expr,
        msgFwdFees: Int257Expr,
        initMsgValue: Int257Expr,
        currentState: MessageHandlingState,
        currentMessage: MessageWithMaybeReceiver,
        restActions: TvmStepScopeManager.(MessageHandlingState) -> Unit,
    ) {
        when (currentState) {
            is MessageHandlingState.Failure -> {
                scope.restActions(currentState)
                return
            }

            is MessageHandlingState.Ok -> {
                val initialState = currentState.copy(messageValue = initMsgValue, messageValueBrutto = zeroValue)
                val transformations =
                    createMessageHandlingTransformations(
                        sendRemainingValue,
                        sendRemainingBalance,
                        sendFwdFeesSeparately,
                        computeFees,
                        msgFwdFees,
                        currentMessage,
                        scope.calcOnState { currentContract },
                    )
                applyConsecutiveTransformations(
                    scope,
                    initialState,
                    transformations,
                    restActions,
                )
            }
        }
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
                        ?: return@doWithConditions
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
                            message.content.destAddrSlice,
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
                        ?: return null to null

                    val inOpcode =
                        sliceLoadIntTransaction(scope, msgBodySlice, OP_BITS.toInt())?.second
                            ?: return null to null

                    val concreteOp = resolver.eval(inOpcode)
                    val concreteOpHex =
                        concreteOp.bigIntValue().toString(16).padStart(TvmContext.OP_BYTES.toInt(), '0')
                    val concreteHandler = variants[concreteOpHex]

                    if (concreteHandler != null) {
                        scope.assert(inOpcode eq concreteOp)
                            ?: return null to null

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
                            ?: return null

                        visitReserveAction()
                    }

                    resolver.eval(isSendMsgAction).isTrue -> {
                        scope.assert(isSendMsgAction)
                            ?: return null

                        val msg =
                            parseAndPreprocessMessageAction(scope, actionBody, resolver)
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
        resolver: TvmTestStateResolver,
    ): MessageActionParseResult? {
        val (_, sendMsgMode) =
            sliceLoadIntTransaction(scope, slice, 8, false)
                ?: return null
        val msg =
            scope.slicePreloadNextRef(slice)
                ?: return null

        val msgSlice = scope.calcOnState { allocSliceFromCell(msg) }
        makeCellToSliceTransaction(scope, msg, msgSlice) // for further TL-B readings

        val ptr = ParsingState(msgSlice)
        val (msgFull, msgValue, destination, fwdFeeInfo, bodyCell) =
            parseMessageInfo(scope, ptr, resolver)
                ?: return null

        scope.doWithState {
            forwardFees = forwardFees.add(fwdFeeInfo)
        }

        val bodySlice = scope.calcOnState { allocSliceFromCell(bodyCell) }

        return MessageActionParseResult(
            MessageAsStackArguments(msgValue, msgFull, bodySlice, destination),
            sendMsgMode,
        )
    }

    private data class MessageInfo(
        val msgFull: UHeapRef,
        val msgValue: UExpr<TvmInt257Sort>,
        val destAddrSlice: UHeapRef,
        val fwdFeeInfo: FwdFeeInfo,
        val msgBody: UHeapRef,
    )

    private fun parseMessageInfo(
        scope: TvmStepScopeManager,
        ptr: ParsingState,
        resolver: TvmTestStateResolver,
    ): MessageInfo? =
        with(ctx) {
            val msgFull = scope.calcOnState { allocEmptyBuilder() }

            val tag =
                sliceLoadIntTransaction(scope, ptr.slice, 1)?.second
                    ?: return@with null

            val isInternalCond = tag eq zeroValue
            scope.assert(isInternalCond)
                ?: return@with null

            // int_msg_info$0 ihr_disabled:Bool bounce:Bool bounced:Bool
            for (i in 0..3) {
                val curFlag =
                    sliceLoadIntTransaction(scope, ptr.slice, 1)?.unwrap(ptr)
                        ?: return@with null
                builderStoreIntTransaction(scope, msgFull, curFlag, oneSizeExpr)
                    ?: return@with null
            }

            sliceSkipNoneOrStdAddr(scope, ptr.slice)?.unwrap(ptr)
                ?: return@with null

            val addrCell =
                scope.getCellContractInfoParam(ADDRESS_PARAMETER_IDX)
                    ?: return null
            val addrSlice = scope.calcOnState { allocSliceFromCell(addrCell) }
            scope.calcOnState {
                dataCellInfoStorage.mapper.addAddressSlice(addrSlice)
            }
            builderStoreSliceTransaction(scope, msgFull, addrSlice)
                ?: return null

            // dest:MsgAddressInt
            val destSlice =
                sliceLoadAddrTransaction(scope, ptr.slice)?.unwrap(ptr)
                    ?: return@with null
            builderStoreSliceTransaction(scope, msgFull, destSlice)
                ?: return null

            // value:CurrencyCollection
            val symbolicMsgValue =
                sliceLoadGramsTransaction(scope, ptr.slice)?.unwrap(ptr)
                    ?: return@with null

            builderStoreGramsTransaction(scope, msgFull, symbolicMsgValue)
                ?: return null

            val extraCurrenciesBit =
                sliceLoadIntTransaction(scope, ptr.slice, 1)?.unwrap(ptr)
                    ?: return@with null

            val extraCurrenciesEmptyConstraint = extraCurrenciesBit eq zeroValue
            scope.assert(extraCurrenciesEmptyConstraint)
                ?: return@with null

            // extra_currency_bit
            builderStoreIntTransaction(scope, msgFull, zeroValue, oneSizeExpr)
                ?: return@with null

            // ihr_fee:Grams - ignore given value and write zero?
            sliceLoadGramsTransaction(scope, ptr.slice)?.unwrap(ptr)
                ?: return@with null
            builderStoreIntTransaction(scope, msgFull, zeroValue, fourSizeExpr)
                ?: return@with null

            // fwd_fee:Grams - ignore given value
            sliceLoadGramsTransaction(scope, ptr.slice)?.unwrap(ptr)
                ?: return@with null
            val fwdFeeSymbolic =
                scope.calcOnState {
                    makeSymbolicPrimitive(mkBvSort(TvmContext.BITS_FOR_FWD_FEE)).zeroExtendToSort(int257sort)
                }
            builderStoreGramsTransaction(scope, msgFull, fwdFeeSymbolic)
                ?: return@with null

            // store the tail
            builderStoreSliceTransaction(scope, msgFull, ptr.slice)
                ?: return null

            // created_lt:uint64 created_at:uint32
            sliceLoadIntTransaction(scope, ptr.slice, 64)?.unwrap(ptr)
                ?: return@with null
            sliceLoadIntTransaction(scope, ptr.slice, 32)?.unwrap(ptr)
                ?: return@with null

            val stateInitBit =
                sliceLoadIntTransaction(scope, ptr.slice, 1)?.unwrap(ptr)
                    ?: return@with null

            val stateInitIsMissing = stateInitBit eq zeroValue

            val stateInitRef =
                if (resolver.eval(stateInitIsMissing).isTrue) {
                    scope.assert(stateInitIsMissing)
                        ?: return@with null

                    null
                } else {
                    scope.assert(stateInitIsMissing.not())
                        ?: return@with null

                    val stateInitInlineBit =
                        sliceLoadIntTransaction(scope, ptr.slice, 1)?.unwrap(ptr)
                            ?: return@with null

                    val stateInitIsInlined = stateInitInlineBit eq zeroValue

                    if (resolver.eval(stateInitIsInlined).isFalse) {
                        scope.assert(stateInitIsInlined.not())
                            ?: return@with null

                        sliceLoadRefTransaction(scope, ptr.slice)?.unwrap(ptr)
                            ?: return@with null
                    } else {
                        scope.assert(stateInitIsInlined)
                            ?: return@with null

                        // fixed_prefix_length:(Maybe (## 5)) special:(Maybe TickTock)
                        val stateInitPrefix =
                            sliceLoadIntTransaction(scope, ptr.slice, 2)?.unwrap(ptr)
                                ?: return@with null
                        scope.assert(stateInitPrefix eq zeroValue)
                            ?: return@with null

                        // code:(Maybe ^Cell)
                        loadMaybeRef(scope, ptr, resolver)
                            ?: return@with null

                        // code:(Maybe ^Cell)
                        loadMaybeRef(scope, ptr, resolver)
                            ?: return@with null

                        // library:(Maybe ^Cell)
                        loadMaybeRef(scope, ptr, resolver)
                            ?: return@with null

                        null
                    }
                }

            val bodyBit =
                sliceLoadIntTransaction(scope, ptr.slice, 1)?.unwrap(ptr)
                    ?: return@with null

            val bodyBitIsInlined = bodyBit eq zeroValue

            val (bodyRefOriginal, bodyRef) =
                if (resolver.eval(bodyBitIsInlined).isFalse) {
                    scope.assert(bodyBitIsInlined.not())
                        ?: return@with null

                    sliceLoadRefTransaction(scope, ptr.slice)
                        ?.unwrap(ptr)
                        ?.let { it to it }
                        ?: return@with null
                } else {
                    scope.assert(bodyBitIsInlined)
                        ?: return@with null

                    val bodyBuilder = scope.calcOnState { allocEmptyBuilder() }
                    builderStoreSliceTransaction(scope, bodyBuilder, ptr.slice)
                        ?: return null
                    val newBody = scope.builderToCell(bodyBuilder)

                    null to newBody
                }

            val fwdFeeInfo = FwdFeeInfo(fwdFeeSymbolic, stateInitRef, bodyRefOriginal)

            return MessageInfo(scope.builderToCell(msgFull), symbolicMsgValue, destSlice, fwdFeeInfo, bodyRef)
        }

    private fun loadMaybeRef(
        scope: TvmStepScopeManager,
        ptr: ParsingState,
        resolver: TvmTestStateResolver,
    ): Unit? =
        scope.doWithCtx {
            val maybeBit =
                sliceLoadIntTransaction(scope, ptr.slice, 1)?.unwrap(ptr)
                    ?: return@doWithCtx null

            val refIsMissing = maybeBit eq zeroValue

            if (resolver.eval(refIsMissing).isTrue) {
                scope.assert(refIsMissing)
                    ?: return@doWithCtx null
            } else {
                scope.assert(refIsMissing.not())
                    ?: return@doWithCtx null

                sliceLoadRefTransaction(scope, ptr.slice)?.unwrap(ptr)
                    ?: return@doWithCtx null
            }

            Unit
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
