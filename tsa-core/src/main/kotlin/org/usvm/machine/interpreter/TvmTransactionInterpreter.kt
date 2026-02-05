package org.usvm.machine.interpreter

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.ton.DestinationDescription
import org.ton.LinearDestinations
import org.ton.OpcodeToDestination
import org.ton.bytecode.ADDRESS_PARAMETER_IDX
import org.ton.bytecode.TsaArtificialActionParseInst
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
import org.usvm.machine.TvmContext.Companion.OP_BITS
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.bigIntValue
import org.usvm.machine.dropFirstWithoutChecks
import org.usvm.machine.splitHeadTail
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.IncompatibleMessageModes
import org.usvm.machine.state.InsufficientFunds
import org.usvm.machine.state.TvmCellUnderflowError
import org.usvm.machine.state.TvmDoubleSendRemainingValue
import org.usvm.machine.state.TvmResult
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.getBalance
import org.usvm.machine.state.getCellContractInfoParam
import org.usvm.machine.state.getContractInfoParamOf
import org.usvm.machine.state.getInboundMessageValue
import org.usvm.machine.state.getSliceRemainingRefsCount
import org.usvm.machine.state.makeCellToSliceNoFork
import org.usvm.machine.state.messages.ActionParseResult
import org.usvm.machine.state.messages.FwdFeeInfo
import org.usvm.machine.state.messages.MessageActionParseResult
import org.usvm.machine.state.messages.MessageMode
import org.usvm.machine.state.messages.Ok
import org.usvm.machine.state.messages.ParsingState
import org.usvm.machine.state.messages.ReserveAction
import org.usvm.machine.state.messages.TlbInternalMessageContent
import org.usvm.machine.state.messages.ValueOrDeadScope
import org.usvm.machine.state.messages.asCellRefUnsafe
import org.usvm.machine.state.messages.asSlice
import org.usvm.machine.state.messages.calculateTwoThirdLikeInTVM
import org.usvm.machine.state.messages.scopeDied
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.sliceLoadIntTlbNoFork
import org.usvm.machine.state.sliceLoadRefTransaction
import org.usvm.machine.state.slicePreloadNextRef
import org.usvm.machine.state.slicesAreEqual
import org.usvm.machine.types.SliceRef
import org.usvm.machine.types.asCellRef
import org.usvm.machine.types.asSliceRef
import org.usvm.mkSizeExpr
import org.usvm.test.resolver.TvmTestStateResolver

private typealias MsgHandlingPredicate = TvmTransactionInterpreter.MessageHandlingState.Ok.() -> UExpr<KBoolSort>
private typealias Transformation =
    TvmTransactionInterpreter.MessageHandlingState.Ok.() -> TvmTransactionInterpreter.MessageHandlingState

private typealias MutableTransformation =
    TvmTransactionInterpreter.MessageHandlingState.OkBuilder.() -> TvmTransactionInterpreter.MessageHandlingState

class TvmTransactionInterpreter(
    val ctx: TvmContext,
) {
    sealed interface ResolvedAndParsedAction

    data object ParsedReserveAction : ResolvedAndParsedAction

    sealed interface ParsedMessageWithResolvedReceiver : ResolvedAndParsedAction {
        val receiver: ContractId?
        val content: TlbInternalMessageContent?
        val sendMessageMode: Int257Expr

        companion object {
            fun construct(
                receiver: ContractId?,
                outMessage: MessageActionParseResult,
            ): ParsedMessageWithResolvedReceiver =
                if (receiver == null) {
                    ParsedMessageWithResolvedNullReceiver(outMessage.sendMessageMode, outMessage.content)
                } else {
                    ParsedMessageWithResolvedNonnullReceiver(receiver, outMessage.sendMessageMode, outMessage.content)
                }
        }
    }

    data class ParsedMessageWithResolvedNullReceiver(
        override val sendMessageMode: Int257Expr,
        override val content: TlbInternalMessageContent?,
    ) : ParsedMessageWithResolvedReceiver {
        override val receiver: ContractId? = null
    }

    data class ParsedMessageWithResolvedNonnullReceiver(
        override val receiver: ContractId,
        override val sendMessageMode: Int257Expr,
        override val content: TlbInternalMessageContent?,
    ) : ParsedMessageWithResolvedReceiver

    /**
     * @property parsedOrderedActions are ordered in the same way they were in the action list
     */
    class ActionsParsingResult(
        val parsedOrderedActions: List<ResolvedAndParsedAction>,
    )

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
            var sentMessages: PersistentList<DispatchedUnconstructedMessage> = persistentListOf(),
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
            val sentMessages: PersistentList<DispatchedUnconstructedMessage> = persistentListOf(),
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

            fun cellUnderflow() = RealFailure(TvmCellUnderflowError)
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
        actions: List<ResolvedAndParsedAction>,
        restActions: TvmStepScopeManager.(ActionHandlingResult) -> Unit?,
    ) {
        // TODO: consider [RESERVE] actions
        val messages = actions.filterIsInstance<ParsedMessageWithResolvedReceiver>()

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
        return scope.handleMessagesImpl(messages, messageHandlingState, compatibleRestActions)
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
            val mode = head.sendMessageMode
            val sendRemainingValue = mode.hasBitSet(MessageMode.SEND_REMAINING_VALUE_BIT)
            val sendRemainingBalance = mode.hasBitSet(MessageMode.SEND_REMAINING_BALANCE_BIT)
            val sendFwdFeesSeparately = mode.hasBitSet(MessageMode.SEND_FEES_SEPARATELY)
            val sendIgnoreErrors = mode.hasBitSet(MessageMode.SEND_IGNORE_ERRORS)

            val content = head.content
            if (content != null) {
                this@handleMessagesImpl.handleSuccessfullyParsedMessage(
                    content,
                    sendRemainingValue,
                    sendRemainingBalance,
                    sendFwdFeesSeparately,
                    this@with,
                    currentMessageHandlingState,
                    head,
                    sendIgnoreErrors,
                ) { stateAfterHandlingMessage ->
                    handleMessagesImpl(tail, stateAfterHandlingMessage, restActions)
                }
            } else {
                concretizeBySplit(sendIgnoreErrors) { sendIgnoreErrors ->
                    if (sendIgnoreErrors) {
                        // eat the error and continue the handling
                        handleMessagesImpl(tail, currentMessageHandlingState, restActions)
                    } else {
                        val result = MessageHandlingState.cellUnderflow()
                        restActions(result)
                    }
                }
            }
        }

    private fun TvmStepScopeManager.concretizeBySplit(
        flag: UBoolExpr,
        restActions: TvmStepScopeManager.(Boolean) -> Unit,
    ) {
        val actions =
            listOf(
                TvmStepScopeManager.ActionOnCondition(
                    action = {},
                    caseIsExceptional = false,
                    condition = flag,
                    paramForDoForAllBlock = true,
                ),
                TvmStepScopeManager.ActionOnCondition(
                    action = {},
                    caseIsExceptional = false,
                    condition = with(ctx) { flag.not() },
                    paramForDoForAllBlock = false,
                ),
            )
        doWithConditions(actions, restActions)
    }

    private fun TvmStepScopeManager.handleSuccessfullyParsedMessage(
        content: TlbInternalMessageContent,
        sendRemainingValue: UBoolExpr,
        sendRemainingBalance: UBoolExpr,
        sendFwdFeesSeparately: UBoolExpr,
        context: TvmContext,
        currentMessageHandlingState: MessageHandlingState,
        currentMessage: ParsedMessageWithResolvedReceiver,
        sendIgnoreErrors: UBoolExpr,
        restActions: TvmStepScopeManager.(MessageHandlingState) -> Unit,
    ) {
        val messageValue = content.commonMessageInfo.msgValue
        this.ctx.handleSingleMessage(
            scope = this,
            sendRemainingValue = sendRemainingValue,
            sendRemainingBalance = sendRemainingBalance,
            sendFwdFeesSeparately = sendFwdFeesSeparately,
            computeFees = context.zeroValue,
            initMsgValue = messageValue,
            currentState = currentMessageHandlingState,
            currentMessage = currentMessage,
        ) { newCurrentState ->
            val actions =
                listOf(
                    TvmStepScopeManager.ActionOnCondition(
                        action = {},
                        caseIsExceptional = false,
                        condition = sendIgnoreErrors,
                        paramForDoForAllBlock =
                            if (newCurrentState is MessageHandlingState.RealFailure) {
                                currentMessageHandlingState
                            } else {
                                newCurrentState
                            },
                    ),
                    TvmStepScopeManager.ActionOnCondition(
                        action = {},
                        caseIsExceptional = false,
                        condition = with(this.ctx) { sendIgnoreErrors.not() },
                        paramForDoForAllBlock = newCurrentState,
                    ),
                )
            doWithConditions(
                actions,
            ) { stateAfterHandlingMessage ->
                restActions(stateAfterHandlingMessage)
            }
        }
    }

    private fun ParsedMessageWithResolvedReceiver.withSetMessageValue(
        messageValue: Int257Expr,
    ): ParsedMessageWithResolvedReceiver {
        val oldContent = content
        val content = oldContent?.copy(commonMessageInfo = oldContent.commonMessageInfo.copy(msgValue = messageValue))
        return when (this) {
            is ParsedMessageWithResolvedNonnullReceiver -> copy(content = content)
            is ParsedMessageWithResolvedNullReceiver -> copy(content = content)
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
        val content = currentMessage.content
        requireNotNull(content) {
            "Only nonnull content should reach here"
        }
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
                                content.commonMessageInfo.copy(
                                    msgValue = this.messageValue,
                                    fwdFee = ctx.calculateTwoThirdLikeInTVM(msgFwdFees),
                                )
                            sentMessages =
                                sentMessages.add(
                                    DispatchedUnconstructedMessage(
                                        receiver = currentMessage.receiver,
                                        content =
                                            content.copy(commonMessageInfo = updatedCommonMessageInfo),
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
        val content = currentMessage.content
        requireNotNull(content) {
            "The caller must check that the content is non-null"
        }
        val fwdFeeInfo =
            FwdFeeInfo(
                fwdFeeSymbolic,
                content.stateInit.asCellRefUnsafe()?.value,
                content.bodyOriginalRef,
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

    /**
     * @return the possible results
     */
    fun parseSingleActionSlice(
        scope: TvmStepScopeManager,
        actionSlice: SliceRef,
        originalStmt: TsaArtificialActionParseInst,
    ): ValueOrDeadScope<List<Pair<ActionParseResult, UBoolExpr>>?> =
        with(scope.ctx) {
            val model = scope.calcOnState { models.first() }
            val resolver =
                TvmTestStateResolver(
                    ctx,
                    model,
                    scope.calcOnState { this },
                    ctx.tvmOptions.performAdditionalChecksWhileResolving,
                )
            val (actionBody, tag) =
                sliceLoadIntTlbNoFork(scope, actionSlice.value, 32)
                    ?: return scopeDied

            val isSendMsgAction = tag eq sendMsgActionTag.unsignedExtendToInteger()
            val isReserveAction = tag eq reserveActionTag.unsignedExtendToInteger()

            when {
                resolver.eval(isReserveAction).isTrue -> {
                    scope.assert(isReserveAction)
                        ?: return scopeDied

                    Ok(listOf(ReserveAction to trueExpr))
                }

                resolver.eval(isSendMsgAction).isTrue -> {
                    scope.assert(isSendMsgAction)
                        ?: return scopeDied

                    val msgs =
                        parseAndPreprocessMessageAction(
                            scope,
                            resolver,
                            actionBody,
                            originalStmt,
                            originalStmt.destinationResolver,
                        )
                            ?: return scopeDied

                    Ok(msgs)
                }

                else -> {
                    error("Unknown action in C5 register")
                }
            }
        }

    fun resolveMessageReceivers(
        scope: TvmStepScopeManager,
        actions: List<ActionParseResult>,
        restActions: TvmStepScopeManager.(ActionsParsingResult) -> Unit,
    ) {
        val actionsParsingResult =
            ActionsParsingResult(
                actions.map {
                    when (it) {
                        is ReserveAction -> {
                            ParsedReserveAction
                        }

                        is MessageActionParseResult -> {
                            ParsedMessageWithResolvedReceiver.construct(
                                it.resolvedReceiver,
                                MessageActionParseResult(it.content, it.sendMessageMode, it.resolvedReceiver),
                            )
                        }
                    }
                },
            )
        return scope.restActions(actionsParsingResult)
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

    fun extractListOfActions(
        scope: TvmStepScopeManager,
        actionsCell: UHeapRef,
    ): List<SliceRef>? =
        with(ctx) {
            var cur = actionsCell.asCellRef()
            val actionList = mutableListOf<SliceRef>()

            while (true) {
                val slice = scope.calcOnState { allocSliceFromCell(cur) }
                val remainingRefs = scope.calcOnState { getSliceRemainingRefsCount(slice.value) }

                val isEnd =
                    scope.checkCondition(remainingRefs eq zeroSizeExpr)
                        ?: return null
                if (isEnd) {
                    // TODO check that `remainingBits` is also zero
                    break
                }

                val action =
                    sliceLoadRefTransaction(scope, slice.value)?.let {
                        cur = it.second
                        it.first
                    }
                        ?: return null
                actionList.add(action.asSliceRef())

                if (actionList.size > TvmContext.MAX_ACTIONS) {
                    // TODO set error code
                    return null
                }
            }

            return actionList.reversed()
        }

    private fun parseAndPreprocessMessageAction(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
        slice: UHeapRef,
        originalStmt: TsaArtificialActionParseInst,
        handler: DestinationDescription?,
    ): List<Pair<MessageActionParseResult, UBoolExpr>>? {
        val (_, sendMsgMode) =
            sliceLoadIntTlbNoFork(scope, slice, 8, false)
                ?: return null
        val msg =
            scope.slicePreloadNextRef(slice)
                ?: return null

        val msgSlice = scope.calcOnState { allocSliceFromCell(msg) }
        makeCellToSliceNoFork(scope, msg, msgSlice) // for further TL-B readings

        val nextStmtAction: TvmState.() -> Unit = {
            val nextStmt =
                originalStmt.copy(
                    yetUnparsedActions =
                        originalStmt.yetUnparsedActions.dropFirstWithoutChecks(),
                    parsedAndPreprocessedActions =
                        originalStmt.parsedAndPreprocessedActions +
                            MessageActionParseResult(
                                null,
                                sendMsgMode,
                                null,
                            ),
                )
            newStmt(nextStmt)
        }
        val messageContentActual =
            parseMessageInfo(scope, resolver, msgSlice, nextStmtAction)
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

        val receiverOptions =
            when (handler) {
                is LinearDestinations -> {
                    val index = originalStmt.parsedAndPreprocessedActions.size
                    val receiver = handler.destinations[index]
                    listOf(receiver)
                }

                is OpcodeToDestination -> {
                    val destinationVariants =
                        run {
                            val (result, innerStatus) =
                                chooseHandlerBasedOnOpcode(
                                    messageContent.body.asSlice().value,
                                    handler.outOpcodeToDestination,
                                    handler.other,
                                    resolver,
                                    scope,
                                )

                            innerStatus ?: return null

                            result ?: listOf(null)
                        }
                    destinationVariants
                }

                null -> {
                    listOf(null)
                }
            }

        return receiverOptions.map { possibleReceiver ->

            val equality =
                run {
                    if (possibleReceiver != null) {
                        val destinationContractAddress =
                            scope.calcOnState {
                                (
                                    getContractInfoParamOf(
                                        ADDRESS_PARAMETER_IDX,
                                        possibleReceiver,
                                    ).cellValue as? UConcreteHeapRef
                                )?.let { allocSliceFromCell(it) }
                                    ?: error("Cannot extract contract address")
                            }
                        scope.slicesAreEqual(
                            destinationContractAddress,
                            messageContentActual.commonMessageInfo.dstAddressSlice,
                        ) ?: return null
                    } else {
                        ctx.trueExpr
                    }
                }

            MessageActionParseResult(
                messageContent,
                sendMsgMode,
                possibleReceiver,
            ) to equality
        }
    }

    private fun parseMessageInfo(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver,
        msgSlice: UConcreteHeapRef,
        nextStmtAction: TvmState.() -> Unit,
    ): TlbInternalMessageContent? =
        with(ctx) {
            val ptr = ParsingState(msgSlice)
            val tag =
                sliceLoadIntTlbNoFork(scope, ptr.slice, 1)?.second
                    ?: return@with null
            val isInternalCond = tag eq zeroValue
            scope.assert(isInternalCond)
                ?: return@with null
            val messageContent =
                TlbInternalMessageContent.extractFromSlice(scope, ptr, resolver, quietBlock = nextStmtAction)
                    ?: return@with null

            return messageContent
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

fun <T> chooseHandlerBasedOnOpcode(
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
                    sliceLoadIntTlbNoFork(scope, msgBodySlice, OP_BITS.toInt())?.second
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
