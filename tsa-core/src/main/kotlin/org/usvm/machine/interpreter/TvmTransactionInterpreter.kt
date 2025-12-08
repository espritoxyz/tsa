package org.usvm.machine.interpreter

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
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
import org.usvm.logger
import org.usvm.machine.Int257Expr
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.OP_BITS
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.asIntValue
import org.usvm.machine.bigIntValue
import org.usvm.machine.splitHeadTail
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.IncompatibleMessageModes
import org.usvm.machine.state.InsufficientFunds
import org.usvm.machine.state.TvmDoubleSendRemainingValue
import org.usvm.machine.state.TvmResult
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.getBalance
import org.usvm.machine.state.getCellContractInfoParam
import org.usvm.machine.state.getContractInfoParamOf
import org.usvm.machine.state.getInboundMessageValue
import org.usvm.machine.state.getSliceRemainingRefsCount
import org.usvm.machine.state.makeCellToSliceNoFork
import org.usvm.machine.state.messages.FwdFeeInfo
import org.usvm.machine.state.messages.MessageActionParseResult
import org.usvm.machine.state.messages.MessageMode
import org.usvm.machine.state.messages.Ok
import org.usvm.machine.state.messages.ParsingState
import org.usvm.machine.state.messages.TlbInternalMessageContent
import org.usvm.machine.state.messages.ValueOrDeadScope
import org.usvm.machine.state.messages.bodySlice
import org.usvm.machine.state.messages.calculateTwoThirdLikeInTVM
import org.usvm.machine.state.messages.getMsgBodySlice
import org.usvm.machine.state.messages.scopeDied
import org.usvm.machine.state.sliceLoadIntTransaction
import org.usvm.machine.state.sliceLoadRefTransaction
import org.usvm.machine.state.slicePreloadNextRef
import org.usvm.machine.state.slicesAreEqual
import org.usvm.machine.types.SliceRef
import org.usvm.machine.types.TvmModel
import org.usvm.mkSizeExpr
import org.usvm.utils.intValueOrNull

private typealias MsgHandlingPredicate = TvmTransactionInterpreter.MessageHandlingState.Ok.() -> UExpr<KBoolSort>
private typealias Transformation =
    TvmTransactionInterpreter.MessageHandlingState.Ok.() -> TvmTransactionInterpreter.MessageHandlingState

private typealias MutableTransformation =
    TvmTransactionInterpreter.MessageHandlingState.OkBuilder.() -> TvmTransactionInterpreter.MessageHandlingState

class TvmTransactionInterpreter(
    val ctx: TvmContext,
) {
    sealed interface ParsedMessageWithResolvedReceiver {
        val receiver: ContractId?
        val outMessage: MessageActionParseResult

        companion object {
            fun construct(
                receiver: ContractId?,
                outMessage: MessageActionParseResult,
            ): ParsedMessageWithResolvedReceiver =
                if (receiver == null) {
                    ParsedMessageWithResolvedNullReceiver(outMessage)
                } else {
                    ParsedMessageWithResolvedNonnullReceiver(receiver, outMessage)
                }
        }
    }

    data class ParsedMessageWithResolvedNullReceiver(
        override val outMessage: MessageActionParseResult,
    ) : ParsedMessageWithResolvedReceiver {
        override val receiver: ContractId? = null
    }

    data class ParsedMessageWithResolvedNonnullReceiver(
        override val receiver: ContractId,
        override val outMessage: MessageActionParseResult,
    ) : ParsedMessageWithResolvedReceiver

    /**
     * @property parsedOrderedMessages are ordered in the same way they were in the action list
     */
    class ActionsParsingResult(
        val parsedOrderedMessages: List<ParsedMessageWithResolvedReceiver>,
    ) {
        val messagesForQueue: List<ParsedMessageWithResolvedNonnullReceiver>
            get() =
                parsedOrderedMessages.filterIsInstance<ParsedMessageWithResolvedNonnullReceiver>()
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
            var sentMessages: PersistentList<DispatchedMessage> = persistentListOf(),
            var alreadyHasMsgsWithSendRemainingValue: UBoolExpr,
        ) {
            fun build() =
                Ok(
                    ctx,
                    currentContractBalance,
                    messageValue,
                    messageValueBrutto,
                    remainingInboundMessageValue,
                    sentMessages,
                    alreadyHasMsgsWithSendRemainingValue,
                )
        }

        data class Ok(
            val ctx: TvmContext,
            val remainingBalance: UExpr<TvmInt257Sort>,
            val messageValue: UExpr<TvmInt257Sort>,
            val messageValueBrutto: UExpr<TvmInt257Sort>,
            val remainingInboundMessageValue: UExpr<TvmInt257Sort>,
            val sentMessages: PersistentList<DispatchedMessage> = persistentListOf(),
            val alreadyHasMsgsWithSendRemainingValue: UBoolExpr,
        ) : MessageHandlingState {
            fun toBuilder() =
                OkBuilder(
                    ctx,
                    remainingBalance,
                    messageValue,
                    messageValueBrutto,
                    remainingInboundMessageValue,
                    sentMessages,
                    alreadyHasMsgsWithSendRemainingValue,
                )
        }

        sealed interface Failure : MessageHandlingState

        data class RealFailure(
            val exit: TvmResult.TvmErrorExit,
        ) : Failure

        data class SoftFailure(
            val exit: TvmResult.TvmSoftFailureExit,
        ) : Failure

        companion object {
            fun insufficientFundsError(contractId: ContractId) = RealFailure(InsufficientFunds(contractId))

            fun incompatibleModsError(contractId: ContractId) = RealFailure(IncompatibleMessageModes(contractId))

            fun doubleSendRemainingValue(contractId: ContractId) = SoftFailure(TvmDoubleSendRemainingValue(contractId))
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

    fun handleMessageCosts(
        scope: TvmStepScopeManager,
        messages: List<ParsedMessageWithResolvedReceiver>,
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
                    persistentListOf(),
                    ctx.falseExpr,
                )
            }
        val compatibleRestActions: TvmStepScopeManager.(MessageHandlingState) -> Unit = {
            val arg =
                when (it) {
                    is MessageHandlingState.RealFailure -> {
                        ActionHandlingResult.RealFailure(it.exit)
                    }

                    is MessageHandlingState.SoftFailure -> {
                        ActionHandlingResult.SoftFailure(it.exit)
                    }

                    is MessageHandlingState.Ok -> {
                        ActionHandlingResult.Success(
                            it.remainingBalance,
                            it.sentMessages,
                        )
                    }
                }
            this.restActions(arg)
        }
        scope.handleMessagesImpl(messages, messageHandlingState, compatibleRestActions)
    }

    private fun TvmStepScopeManager.handleMessagesImpl(
        messages: List<ParsedMessageWithResolvedReceiver>,
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
            val sendIgnoreErrors =
                mode
                    .hasBitSet(MessageMode.SEND_IGNORE_ERRORS)
                    .asIntValue()
                    .intValueOrNull
                    ?.let { it != 0 }
                    ?: error("Only concrete mode is supported")
            val messageValue = head.outMessage.content.commonMessageInfo.msgValue
            ctx.handleSingleMessage(
                scope = this@handleMessagesImpl,
                sendRemainingValue = sendRemainingValue,
                sendRemainingBalance = sendRemainingBalance,
                sendFwdFeesSeparately = sendFwdFeesSeparately,
                computeFees = zeroValue,
                initMsgValue = messageValue,
                currentState = currentMessageHandlingState,
                currentMessage = head,
            ) { newCurrentState ->
                val newCurrentStateWithPossiblyIgnoredError =
                    if (newCurrentState is MessageHandlingState.RealFailure && sendIgnoreErrors) {
                        currentMessageHandlingState
                    } else {
                        newCurrentState
                    }
                handleMessagesImpl(tail, newCurrentStateWithPossiblyIgnoredError, restActions)
            }
        }

    private fun ParsedMessageWithResolvedReceiver.withSetMessageValue(
        messageValue: Int257Expr,
    ): ParsedMessageWithResolvedReceiver {
        val oldContent = outMessage.content
        val content = oldContent.copy(commonMessageInfo = oldContent.commonMessageInfo.copy(msgValue = messageValue))
        return when (this) {
            is ParsedMessageWithResolvedNonnullReceiver -> copy(outMessage = outMessage.copy(content = content))
            is ParsedMessageWithResolvedNullReceiver -> copy(outMessage = outMessage.copy(content = content))
        }
    }

    /**
     *
     * See `org.ton.docs.ContractState.processFeesOfMessage` in the test module for the code-like
     * documentation of what is happening here.
     * @return list of transformations that are applied to the initial state during the message handling
     *
     */
    private fun TvmContext.createMessageHandlingTransformations(
        sendRemainingValue: UBoolExpr,
        sendRemainingBalance: UBoolExpr,
        sendFwdFeesSeparately: UBoolExpr,
        computeFees: Int257Expr,
        msgFwdFees: Int257Expr,
        currentMessage: ParsedMessageWithResolvedReceiver,
        currentContractId: ContractId,
    ): List<List<CondTransform>> {
        val payFeesSeparately = sendFwdFeesSeparately and sendRemainingBalance.not()
        val transform1: List<CondTransform> =
            listOf(
                CondTransform(
                    predicate = {
                        alreadyHasMsgsWithSendRemainingValue and sendRemainingValue
                    },
                    transform =
                        asOnCopy {
                            MessageHandlingState.doubleSendRemainingValue(currentContractId)
                        },
                ),
                CondTransform(
                    predicate = {
                        (alreadyHasMsgsWithSendRemainingValue and sendRemainingValue).not()
                    },
                    transform = asOnCopy { build() },
                ),
            )
        val transform2: List<CondTransform> =
            listOf(
                CondTransform(
                    predicate = { sendRemainingBalance and sendRemainingValue.not() },
                    transform =
                        asOnCopy {
                            remainingInboundMessageValue = zeroValue
                            messageValue = currentContractBalance
                            build()
                        },
                ),
                CondTransform(
                    predicate = { sendRemainingBalance and sendRemainingValue },
                    transform =
                        asOnCopy {
                            MessageHandlingState.incompatibleModsError(currentContractId)
                        },
                ),
                CondTransform(
                    predicate = {
                        sendRemainingBalance.not() and sendRemainingValue and
                            ((messageValue bvAdd remainingInboundMessageValue) bvUge computeFees)
                    },
                    transform =
                        asOnCopy {
                            messageValue = messageValue bvAdd remainingInboundMessageValue bvSub computeFees
                            remainingInboundMessageValue = zeroValue
                            build()
                        },
                ),
                CondTransform(
                    predicate = {
                        sendRemainingBalance.not() and sendRemainingValue and
                            ((messageValue bvAdd remainingInboundMessageValue) bvUge computeFees).not()
                    },
                    transform =
                        asOnCopy {
                            MessageHandlingState.insufficientFundsError(currentContractId)
                        },
                ),
                CondTransform(
                    predicate = {
                        sendRemainingBalance.not() and sendRemainingValue.not()
                    },
                    transform = asOnCopy { build() },
                ),
            )
        val transform3: List<CondTransform> =
            listOf(
                CondTransform(
                    predicate = { payFeesSeparately },
                    transform =
                        asOnCopy {
                            messageValueBrutto = messageValue bvAdd msgFwdFees
                            build()
                        },
                ),
                CondTransform(
                    predicate = { payFeesSeparately.not() and (this.messageValue bvUge msgFwdFees) },
                    transform =
                        asOnCopy {
                            messageValueBrutto = messageValue
                            messageValue = messageValue bvSub msgFwdFees
                            build()
                        },
                ),
                CondTransform(
                    predicate = { payFeesSeparately.not() and (this.messageValue bvUge msgFwdFees).not() },
                    transform =
                        asOnCopy {
                            MessageHandlingState.insufficientFundsError(currentContractId)
                        },
                ),
            )
        val transform4: List<CondTransform> =
            listOf(
                CondTransform(
                    predicate = { remainingBalance bvUge messageValueBrutto },
                    transform =
                        asOnCopy {
                            currentContractBalance = currentContractBalance bvSub messageValueBrutto
                            currentMessage.withSetMessageValue(messageValue)
                            val updatedCommonMessageInfo =
                                currentMessage.outMessage.content.commonMessageInfo.copy(
                                    msgValue = this.messageValue,
                                    fwdFee = ctx.calculateTwoThirdLikeInTVM(msgFwdFees),
                                )
                            sentMessages =
                                sentMessages.add(
                                    DispatchedMessage(
                                        receiver = currentMessage.receiver,
                                        content =
                                            currentMessage.outMessage.content.copy(
                                                commonMessageInfo = updatedCommonMessageInfo,
                                            ),
                                    ),
                                )
                            build()
                        },
                ),
                CondTransform(
                    predicate = { (remainingBalance bvUge messageValueBrutto).not() },
                    transform =
                        asOnCopy {
                            MessageHandlingState.insufficientFundsError(currentContractId)
                        },
                ),
            )
        val transformApplyValue: List<CondTransform> =
            listOf(
                CondTransform(
                    predicate = { trueExpr },
                    transform =
                        asOnCopy {
                            alreadyHasMsgsWithSendRemainingValue =
                                ctx.mkOr(alreadyHasMsgsWithSendRemainingValue, sendRemainingValue)
                            build()
                        },
                ),
            )
        val transformations = listOf(transform1, transform2, transform3, transform4, transformApplyValue)
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
            head.mapNotNull {
                val predicate = currentState.applyPredicate(ctx, it.predicate)
                if (predicate.isFalse) {
                    return@mapNotNull null
                }
                TvmStepScopeManager.ActionOnCondition(
                    {},
                    false,
                    predicate,
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
        initMsgValue: Int257Expr,
        currentState: MessageHandlingState,
        currentMessage: ParsedMessageWithResolvedReceiver,
        restActions: TvmStepScopeManager.(MessageHandlingState) -> Unit,
    ) {
        val fwdFeeSymbolic =
            scope.calcOnState {
                with(ctx) { makeSymbolicPrimitive(mkBvSort(TvmContext.BITS_FOR_FWD_FEE)).zeroExtendToSort(int257sort) }
            }
        val fwdFeeInfo =
            FwdFeeInfo(
                fwdFeeSymbolic,
                currentMessage.outMessage.content.stateInitRef,
                currentMessage.outMessage.content.bodyOriginalRef,
            )
        scope.doWithState {
            forwardFees = forwardFees.add(fwdFeeInfo)
        }

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
                        fwdFeeSymbolic,
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

    fun parseSingleActionSlice(
        scope: TvmStepScopeManager,
        actionSlice: SliceRef,
    ): ValueOrDeadScope<MessageActionParseResult?> =
        with(scope.ctx) {
            val resolver = scope.calcOnState { models.first() }
            val (actionBody, tag) =
                sliceLoadIntTransaction(scope, actionSlice.value, 32)
                    ?: return scopeDied

            val isSendMsgAction = tag eq sendMsgActionTag.unsignedExtendToInteger()
            val isReserveAction = tag eq reserveActionTag.unsignedExtendToInteger()

            when {
                resolver.eval(isReserveAction).isTrue -> {
                    scope.assert(isReserveAction)
                        ?: return scopeDied

                    visitReserveAction()
                    Ok(null)
                }

                resolver.eval(isSendMsgAction).isTrue -> {
                    scope.assert(isSendMsgAction)
                        ?: return scopeDied

                    val msg =
                        parseAndPreprocessMessageAction(scope, actionBody, resolver)
                            ?: return scopeDied

                    Ok(msg)
                }

                else -> {
                    error("Unknown action in C5 register")
                }
            }
        }

    fun resolveMessageReceivers(
        scope: TvmStepScopeManager,
        messages: List<MessageActionParseResult>,
        model: TvmModel,
        restActions: TvmStepScopeManager.(ActionsParsingResult) -> Unit,
    ) {
        if (messages.isEmpty()) {
            return ActionsParsingResult(emptyList()).let {
                scope.restActions(it)
            }
        }

        if (!ctx.tvmOptions.intercontractOptions.isIntercontractEnabled) {
            return ActionsParsingResult(messages.map { ParsedMessageWithResolvedNullReceiver(it) }).let {
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
                model,
                scope,
            )

        status ?: return

        if (handler == null) {
            return ActionsParsingResult(messages.map { ParsedMessageWithResolvedNullReceiver(it) }).let {
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
                        .map { (receiver, message) -> ParsedMessageWithResolvedNonnullReceiver(receiver, message) }
                ActionsParsingResult(messagesForQueue).let { scope.restActions(it) }
            }

            is OpcodeToDestination -> {
                val destinationVariants =
                    messages.map {
                        val (result, innerStatus) =
                            chooseHandlerBasedOnOpcode(
                                it.content.tail.bodySlice(),
                                handler.outOpcodeToDestination,
                                handler.other,
                                model,
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
                            destinations.zip(messages) { dest, msg ->
                                ParsedMessageWithResolvedReceiver.construct(dest, msg)
                            }

                        TvmStepScopeManager.ActionOnCondition(
                            caseIsExceptional = false,
                            condition = ctx.trueExpr,
                            paramForDoForAllBlock = ActionsParsingResult(parsedMessages),
                            action = {},
                        )
                    }

                if (combinations.isNotEmpty()) {
                    scope.doWithConditions(actions) { param ->
                        assertCorrectAddresses(this, param.messagesForQueue)
                            ?: return@doWithConditions
                        restActions(param)
                    }
                } else {
                    logger.warn("No actions were found fot the opcode")
                    scope.restActions(ActionsParsingResult(messages.map { ParsedMessageWithResolvedNullReceiver(it) }))
                }
            }
        }
    }

    private fun assertCorrectAddresses(
        scope: TvmStepScopeManager,
        newMessagesForQueue: List<ParsedMessageWithResolvedNonnullReceiver>,
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
                            message.content.commonMessageInfo.dstAddressSlice,
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
        resolver: TvmModel,
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

    fun extractListOfActions(
        scope: TvmStepScopeManager,
        actionsCell: UHeapRef,
    ): List<SliceRef>? =
        with(ctx) {
            var cur = actionsCell
            val actionList = mutableListOf<SliceRef>()

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
                actionList.add(SliceRef(action))

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
        resolver: TvmModel,
    ): MessageActionParseResult? {
        val (_, sendMsgMode) =
            sliceLoadIntTransaction(scope, slice, 8, false)
                ?: return null
        val msg =
            scope.slicePreloadNextRef(slice)
                ?: return null

        val msgSlice = scope.calcOnState { allocSliceFromCell(msg) }
        makeCellToSliceNoFork(scope, msg, msgSlice) // for further TL-B readings

        val ptr = ParsingState(msgSlice)
        val messageContentActual =
            parseMessageInfo(scope, ptr, resolver)
                ?: return null

        val senderAddressCell =
            scope.getCellContractInfoParam(ADDRESS_PARAMETER_IDX)
                ?: return null
        val senderAddressSlice = scope.calcOnState { allocSliceFromCell(senderAddressCell) }

        scope.calcOnState {
            dataCellInfoStorage.mapper.addAddressSlice(senderAddressSlice)
        }

        val commonMessageInfo =
            messageContentActual.commonMessageInfo.copy(
                srcAddressSlice = senderAddressSlice,
            )
        val messageContent =
            messageContentActual.copy(commonMessageInfo = commonMessageInfo)

        return MessageActionParseResult(
            messageContent,
            sendMsgMode,
        )
    }

    private fun parseMessageInfo(
        scope: TvmStepScopeManager,
        ptr: ParsingState,
        resolver: TvmModel,
    ): TlbInternalMessageContent? =
        with(ctx) {
            val tag =
                sliceLoadIntTransaction(scope, ptr.slice, 1)?.second
                    ?: return@with null
            val isInternalCond = tag eq zeroValue
            scope.assert(isInternalCond)
                ?: return@with null
            val messageContent =
                TlbInternalMessageContent.extractFromSlice(scope, ptr, resolver)
                    ?: return@with null

            return messageContent
        }

    private fun visitReserveAction() {
        // TODO no implementation, since we don't compute actions fees and balance
        return
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
