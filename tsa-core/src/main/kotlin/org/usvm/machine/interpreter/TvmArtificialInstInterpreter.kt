package org.usvm.machine.interpreter

import kotlinx.collections.immutable.persistentListOf
import org.ton.bytecode.ADDRESS_PARAMETER_IDX
import org.ton.bytecode.TsaArtificialActionParseInst
import org.ton.bytecode.TsaArtificialActionPhaseStartInst
import org.ton.bytecode.TsaArtificialBouncePhaseInst
import org.ton.bytecode.TsaArtificialCheckerReturn
import org.ton.bytecode.TsaArtificialExecuteContInst
import org.ton.bytecode.TsaArtificialExitInst
import org.ton.bytecode.TsaArtificialHandleMessagesCostInst
import org.ton.bytecode.TsaArtificialImplicitRetInst
import org.ton.bytecode.TsaArtificialInst
import org.ton.bytecode.TsaArtificialJmpToContInst
import org.ton.bytecode.TsaArtificialLoopEntranceInst
import org.ton.bytecode.TsaArtificialOnComputePhaseExitInst
import org.ton.bytecode.TsaArtificialOnOutMessageHandlerCallInst
import org.ton.bytecode.TsaArtificialPostprocessInst
import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.TvmArtificialInst
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.Int257Expr
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.RECEIVE_INTERNAL_ID
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.splitHeadTail
import org.usvm.machine.state.CheckerCallResult
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmActionPhase
import org.usvm.machine.state.TvmBouncePhase
import org.usvm.machine.state.TvmComputePhase
import org.usvm.machine.state.TvmConstructedMessageCellOverflow
import org.usvm.machine.state.TvmEventInformation
import org.usvm.machine.state.TvmExitPhase
import org.usvm.machine.state.TvmFailureType
import org.usvm.machine.state.TvmMessageDrivenContractExecutionEntry
import org.usvm.machine.state.TvmPostProcessPhase
import org.usvm.machine.state.TvmResult
import org.usvm.machine.state.TvmResult.TvmAbstractSoftFailure
import org.usvm.machine.state.TvmResult.TvmFailure
import org.usvm.machine.state.TvmStack
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.addCell
import org.usvm.machine.state.addInt
import org.usvm.machine.state.addOnStack
import org.usvm.machine.state.addSlice
import org.usvm.machine.state.allocEmptyBuilder
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.builderStoreDataBits
import org.usvm.machine.state.builderToCell
import org.usvm.machine.state.callCheckerMethodIfExists
import org.usvm.machine.state.callContinuation
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.contractEpilogue
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.generateSymbolicTime
import org.usvm.machine.state.getBalance
import org.usvm.machine.state.getContractInfoParamOf
import org.usvm.machine.state.initializeContractExecutionMemory
import org.usvm.machine.state.isExceptional
import org.usvm.machine.state.jumpToContinuation
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.messages.ContractSender
import org.usvm.machine.state.messages.Flags
import org.usvm.machine.state.messages.MessageAfterCommonMsgInfo
import org.usvm.machine.state.messages.MessageAsStackArguments
import org.usvm.machine.state.messages.ReceivedMessage
import org.usvm.machine.state.messages.TlbCommonMessageInfo
import org.usvm.machine.state.messages.TlbInternalMessageContent
import org.usvm.machine.state.messages.getMsgBodySlice
import org.usvm.machine.state.messages.getOrElse
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.readSliceCell
import org.usvm.machine.state.readSliceDataPos
import org.usvm.machine.state.returnFromContinuation
import org.usvm.machine.state.setBalance
import org.usvm.machine.state.setExit
import org.usvm.machine.state.slicePreloadDataBits
import org.usvm.machine.state.switchToFirstMethodInContract
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmSliceType
import org.usvm.sizeSort
import org.usvm.test.resolver.TvmTestStateResolver

/**
 * The order of the artificial instructions after the end of the compute phase is as follows:
 * - In one way or another the compute phase ends
 * - [TsaArtificialOnComputePhaseExitInst]
 * - [TsaArtificialActionPhaseStartInst]
 * - [TsaArtificialActionParseInst] (one for each action in an action list and one for an empty list)
 * - [TsaArtificialHandleMessagesCostInst]
 * - [TsaArtificialOnOutMessageHandlerCallInst] (one for each of the actually sent messages if the handler exists)
 * - [TsaArtificialBouncePhaseInst]
 * - [TsaArtificialExitInst]
 */
class TvmArtificialInstInterpreter(
    val ctx: TvmContext,
    private val contractsCode: List<TsaContractCode>,
    private val transactionInterpreter: TvmTransactionInterpreter,
    private val checkerFunctionsInterpreter: TsaCheckerFunctionsInterpreter,
) {
    fun visit(
        scope: TvmStepScopeManager,
        stmt: TvmArtificialInst,
    ) {
        check(stmt is TsaArtificialInst) {
            "Unexpected artificial instruction: $stmt"
        }

        when (stmt) {
            is TsaArtificialLoopEntranceInst -> {
                scope.consumeDefaultGas(stmt)
                scope.doWithState { newStmt(stmt.nextStmt()) }
            }

            is TsaArtificialImplicitRetInst -> {
                scope.consumeDefaultGas(stmt)

                scope.returnFromContinuation()
            }

            is TsaArtificialJmpToContInst -> {
                scope.consumeDefaultGas(stmt)

                scope.jumpToContinuation(stmt.cont)
            }

            is TsaArtificialExecuteContInst -> {
                scope.consumeDefaultGas(stmt)

                scope.callContinuation(stmt, stmt.cont)
            }

            is TsaArtificialOnOutMessageHandlerCallInst -> {
                scope.consumeDefaultGas(stmt)
                visitOnOutMessageHandlerCall(scope, stmt)
            }

            is TsaArtificialOnComputePhaseExitInst -> {
                scope.consumeDefaultGas(stmt)
                visitOnComputeExitPhase(scope, stmt)
            }

            is TsaArtificialActionPhaseStartInst -> {
                scope.consumeDefaultGas(stmt)

                visitActionPhaseInst(scope, stmt)
            }

            is TsaArtificialActionParseInst -> {
                scope.consumeDefaultGas(stmt)

                visitParseActionInst(scope, stmt)
            }

            is TsaArtificialHandleMessagesCostInst -> {
                scope.consumeDefaultGas(stmt)

                visitHandleMessageCostsInst(scope, stmt)
            }

            is TsaArtificialBouncePhaseInst -> {
                scope.consumeDefaultGas(stmt)

                visitBouncePhaseInst(scope, stmt)
            }

            is TsaArtificialExitInst -> {
                scope.consumeDefaultGas(stmt)

                visitExitInst(scope, stmt)
            }

            is TsaArtificialCheckerReturn -> {
                scope.consumeDefaultGas(stmt)

                checkerFunctionsInterpreter.checkerReturn(scope, stmt)
            }

            is TsaArtificialPostprocessInst -> {
                error("TsaArtificialPostprocessInst should have been processed earlier")
            }
        }
    }

    private fun TvmStepScopeManager.doCallOnOutMessageIfRequired(
        stmt: TsaArtificialOnOutMessageHandlerCallInst,
    ): CheckerCallResult =
        calcOnState {
            if (contractsCode[currentContract].isContractWithTSACheckerFunctions) {
                return@calcOnState CheckerCallResult.WAS_NOT_CALLED
            }
            val (head, tail) =
                stmt.sentMessages.splitHeadTail()
                    ?: return@calcOnState CheckerCallResult.WAS_NOT_CALLED
            val nextInst = stmt.copy(sentMessages = tail, messageOrderNumber = stmt.messageOrderNumber + 1)
            val currentContractToPush = currentContract
            val pushArgsOnStack: TvmState.() -> Unit? = {
                val constructedMessageCells = head.content.toStackArgs()
                val messageCounter = stmt.messageOrderNumber
                with(ctx) {
                    stack.addInt(messageCounter.toBv257())
                    stack.addCell(constructedMessageCells.fullMsgCell)
                    stack.addSlice(constructedMessageCells.msgBodySlice)
                    stack.addInt((head.receiver ?: -1).toBv257()) // receiver
                    stack.addInt(currentContractToPush.toBv257()) // sender
                }
            }

            return@calcOnState callCheckerMethodIfExists(
                ON_OUT_MESSAGE_METHOD_ID.toBigInteger(),
                nextInst,
                contractsCode,
                pushArgsOnStack,
            )
        }

    private fun visitOnOutMessageHandlerCall(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialOnOutMessageHandlerCallInst,
    ) {
        val wasCalled = scope.doCallOnOutMessageIfRequired(stmt)
        scope.doWithState {
            when (wasCalled) {
                CheckerCallResult.WAS_CALLED -> {
                    // do nothing --- control flow is now in the checker
                }

                CheckerCallResult.MSG_CONSTRUCTION_OVERFLOW -> {
                    setExit(TvmResult.TvmSoftFailure(TvmConstructedMessageCellOverflow, phase))
                }

                CheckerCallResult.WAS_NOT_CALLED -> {
                    newStmt(
                        TsaArtificialBouncePhaseInst(
                            stmt.computePhaseResult,
                            stmt.actionPhaseResult,
                            lastStmt.location,
                        ),
                    )
                }
            }
        }
    }

    private fun TvmStepScopeManager.doCallOnComputeExitIfNecessary(
        stmt: TsaArtificialOnComputePhaseExitInst,
    ): CheckerCallResult =
        calcOnState {
            if (contractsCode[currentContract].isContractWithTSACheckerFunctions) {
                return@calcOnState CheckerCallResult.WAS_NOT_CALLED
            }
            val correspondingSymbol =
                currentComputeFeeUsed
                    ?: error("Current compute_fee should be non-null here")
            val calleeContract = currentContract
            val pushArgsOnStack: TvmState.() -> Unit = {
                with(ctx) {
                    stack.addInt(correspondingSymbol)
                    stack.addInt(calleeContract.toBv257())
                }
            }
            val nextInst = TsaArtificialActionPhaseStartInst(stmt.computePhaseResult, stmt.location)
            return@calcOnState callCheckerMethodIfExists(
                ON_COMPUTE_PHASE_EXIT_METHOD_ID.toBigInteger(),
                nextInst,
                contractsCode,
                pushArgsOnStack,
            )
        }

    private fun visitOnComputeExitPhase(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialOnComputePhaseExitInst,
    ) {
        scope.doWithState {
            val isTsaChecker = scope.calcOnState { contractsCode[currentContract].isContractWithTSACheckerFunctions }

            if (!isTsaChecker) {
                // A temporary solution. The more suitable solution would calculate
                // `computeFeeUsed` as the state instructions are executed (possibly with some helper
                // structures). When this happens, this comment and the line below must be deleted
                currentComputeFeeUsed = makeSymbolicPrimitive(ctx.int257sort)
                currentPhaseEndTime = pseudologicalTime

                registerEventIfNeeded(stmt.computePhaseResult)
            }

            val shouldNotCallExitHandler = (scope.ctx.tvmOptions.stopOnFirstError && isExceptional) || isTsaChecker
            if (!shouldNotCallExitHandler) {
                val wasCalled = scope.doCallOnComputeExitIfNecessary(stmt)
                when (wasCalled) {
                    CheckerCallResult.WAS_NOT_CALLED -> {
                        newStmt(TsaArtificialActionPhaseStartInst(stmt.computePhaseResult, lastStmt.location))
                    }

                    CheckerCallResult.MSG_CONSTRUCTION_OVERFLOW -> {
                        setExit(TvmResult.TvmSoftFailure(TvmConstructedMessageCellOverflow, phase))
                    }

                    else -> {
                        // do nothing --- the control flow is now in the checker
                    }
                }
            } else {
                newStmt(TsaArtificialActionPhaseStartInst(stmt.computePhaseResult, lastStmt.location))
            }
        }
    }

    private fun TvmState.registerEventIfNeeded(result: TvmResult) {
        receivedMessage?.let { receivedMessage ->
            val computeFee =
                currentComputeFeeUsed
                    ?: error("Compute fee should be non-null here")

            val phaseEndTime =
                currentPhaseEndTime
                    ?: error("Phase end time should be non-null here")

            eventsLog =
                eventsLog.add(
                    TvmMessageDrivenContractExecutionEntry(
                        id = currentPhaseBeginTime,
                        executionBegin = currentPhaseBeginTime,
                        executionEnd = phaseEndTime,
                        contractId = currentContract,
                        incomingMessage = receivedMessage,
                        computePhaseResult = result,
                        actionPhaseResult = null, // might be set later
                        computeFee = computeFee,
                        eventTime = time,
                    ),
                )
        }
    }

    private fun visitHandleMessageCostsInst(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialHandleMessagesCostInst,
    ) = transactionInterpreter.handleMessageCosts(
        scope,
        stmt.parsingResult.parsedOrderedMessages,
    ) { actionsHandlingResult ->
        when (actionsHandlingResult) {
            is ActionHandlingResult.Success -> {
                val sentMessages =
                    actionsHandlingResult.messagesDispatched.map { msg ->
                        val msgCells =
                            msg.content.constructMessageCellFromContent(
                                this,
                                quietBlock = {
                                    setExit(TvmResult.TvmSoftFailure(TvmConstructedMessageCellOverflow, phase))
                                },
                            )
                                ?: return@handleMessageCosts null
                        DispatchedMessage(msg.receiver, DispatchedMessageContent(msg.content, msgCells))
                    }
                this.calcOnState { registerActionPhaseEffect(sentMessages, actionsHandlingResult.balanceLeft) }
                doWithState {
                    // Workaround, so that on_out_message has access to the balance.
                    // It is ok to make a commit here as the action phase has essentially ended by this point and
                    // the left execution of the current contract consists of calling handlers and returning
                    // control to scheduler.
                    contractIdToFirstElementOfC7 =
                        contractIdToFirstElementOfC7.put(
                            currentContract,
                            registersOfCurrentContract.c7.value[0, stack].cell(
                                stack,
                            ) as TvmStack.TvmStackTupleValueConcreteNew,
                        )
                    newStmt(
                        TsaArtificialOnOutMessageHandlerCallInst(
                            computePhaseResult = stmt.computePhaseResult,
                            actionPhaseResult = TvmResult.TvmActionPhaseSuccess(stmt.computePhaseResult),
                            location = lastStmt.location,
                            sentMessages = sentMessages,
                            messageOrderNumber = 0,
                        ),
                    )
                }
            }

            is ActionHandlingResult.RealFailure -> {
                this.calcOnState {
                    val failure =
                        TvmFailure(
                            actionsHandlingResult.failure,
                            TvmFailureType.UnknownError,
                            phase,
                            pathNode,
                        )
                    newStmt(TsaArtificialExitInst(stmt.computePhaseResult, failure, lastStmt.location))
                }
            }

            is ActionHandlingResult.SoftFailure -> {
                this.calcOnState {
                    val failure =
                        TvmResult.TvmSoftFailure(
                            actionsHandlingResult.failure,
                            phase,
                        )
                    newStmt(TsaArtificialExitInst(stmt.computePhaseResult, failure, lastStmt.location))
                }
            }
        }
    }

    private fun visitParseActionInst(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialActionParseInst,
    ) {
        val (head, tail) =
            stmt.yetUnparsedActions.splitHeadTail() ?: return run {
                transactionInterpreter.resolveMessageReceivers(
                    scope,
                    stmt.parsedAndPreprocessedActions,
                ) { parsingResult ->
                    doWithState {
                        newStmt(
                            TsaArtificialHandleMessagesCostInst(stmt.computePhaseResult, stmt.location, parsingResult),
                        )
                    }
                }
            }
        val possibleParsedHeads =
            transactionInterpreter
                .parseSingleActionSlice(scope, head, stmt)
                .getOrElse { return }
                ?: return
        val actions =
            possibleParsedHeads.map { (parsedHead, condition) ->
                TvmStepScopeManager.ActionOnCondition(
                    action = {
                        val updatedParsedAndPreprocessed = stmt.parsedAndPreprocessedActions
                        val newStmt =
                            stmt.copy(
                                yetUnparsedActions = tail,
                                parsedAndPreprocessedActions = updatedParsedAndPreprocessed + parsedHead,
                            )
                        newStmt(newStmt)
                    },
                    caseIsExceptional = false,
                    condition = condition,
                    paramForDoForAllBlock = Unit,
                )
            }
        scope.calcOnState { isExceptional = false }
        scope.doWithConditions(actions) {}
    }

    private fun visitActionPhaseInst(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialActionPhaseStartInst,
    ) {
        val isTsaChecker = scope.calcOnState { contractsCode[currentContract].isContractWithTSACheckerFunctions }

        val commitedState =
            scope.calcOnState {
                lastCommitedStateOfContracts[currentContract]
            }
        val analyzingReceiver = scope.calcOnState { receivedMessage != null }
        if (analyzingReceiver && commitedState != null && ctx.tvmOptions.enableOutMessageAnalysis && !isTsaChecker) {
            scope.calcOnState {
                // if we are here, we are going to process the action phase and thus need not it to be exceptional
                isExceptional = false
            }
            scope.doWithState {
                phase = TvmActionPhase(stmt.computePhaseResult)
            }
            val commitedActions = scope.calcOnState { commitedState.c5.value.value }
            val actions =
                transactionInterpreter.extractListOfActions(scope, commitedActions)
                    ?: return

            val scheme = ctx.tvmOptions.intercontractOptions.communicationScheme

            val contractId = scope.calcOnState { currentContract }
            val handlers = scheme?.get(contractId)

            val msgBody =
                scope.calcOnState { receivedMessage?.getMsgBodySlice() }
                    ?: error("Unexpected null msg_body")

            val model = scope.calcOnState { models.first() }
            val resolver =
                TvmTestStateResolver(
                    ctx,
                    model,
                    scope.calcOnState { this },
                    ctx.tvmOptions.performAdditionalChecksWhileResolving,
                )
            val (handler, status) =
                if (handlers != null) {
                    chooseHandlerBasedOnOpcode(
                        msgBody,
                        handlers.inOpcodeToDestination,
                        handlers.other,
                        resolver,
                        scope,
                    )
                } else {
                    (null to Unit)
                }
            status ?: return

            scope.calcOnState {
                newStmt(
                    TsaArtificialActionParseInst(
                        stmt.computePhaseResult,
                        lastStmt.location,
                        actions,
                        persistentListOf(),
                        handler,
                    ),
                )
            }
        } else {
            scope.doWithState {
                newStmt(
                    TsaArtificialOnOutMessageHandlerCallInst(
                        computePhaseResult = stmt.computePhaseResult,
                        actionPhaseResult = null,
                        lastStmt.location,
                        sentMessages = emptyList(),
                        0,
                    ),
                )
            }
        }
    }

    private fun visitBouncePhaseInst(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialBouncePhaseInst,
    ) {
        scope.calcOnState {
            phase = TvmBouncePhase(stmt.computePhaseResult, stmt.actionPhaseResult)
        }
        addBounceMessageIfNeeded(scope, stmt)
    }

    private fun addBounceMessageIfNeeded(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialBouncePhaseInst,
    ) {
        val isTsaChecker = scope.calcOnState { contractsCode[currentContract].isContractWithTSACheckerFunctions }
        if (scope.ctx.tvmOptions.stopOnFirstError || isTsaChecker) {
            // sending bounced messages is only considered when the message handling ended with an exception
            // if we stop on the first error, the potential bouncing won't be considered
            scope.doWithState {
                newStmt(TsaArtificialExitInst(stmt.computePhaseResult, stmt.actionPhaseResult, lastStmt.location))
            }
            return
        }

        scope.doWithState {
            // unmark state as exceptional
            isExceptional = false
        }

        scope.calcOnState {
            with(ctx) {
                if (stmt.computePhaseResult is TvmFailure) {
                    val (sender, _, receivedMsgData) =
                        receivedMessage as? ReceivedMessage.MessageFromOtherContract
                            ?: run {
                                newStmt(
                                    TsaArtificialExitInst(
                                        stmt.computePhaseResult,
                                        stmt.actionPhaseResult,
                                        lastStmt.location,
                                    ),
                                )
                                return@calcOnState
                            }
                    // if is bounceable, bounce back to sender
                    val fullMsgData =
                        fieldManagers.cellDataFieldManager.readCellData(scope, receivedMsgData.fullMsgCell)
                            ?: return@calcOnState
                    val isBounceable =
                        mkBvAndExpr(
                            fullMsgData,
                            mkBvShiftLeftExpr(oneCellValue, 1020.toCellSort()),
                        )

                    val bouncedMessage =
                        constructBouncedMessage(scope, receivedMsgData, sender.contractId)
                            ?: return@with
                    scope.fork(
                        isBounceable.neq(zeroCellValue),
                        falseStateIsExceptional = false,
                        blockOnTrueState = {
                            messageQueue =
                                messageQueue.add(
                                    ReceivedMessage.MessageFromOtherContract(
                                        sender = ContractSender(currentContract, currentEventId),
                                        receiver = sender.contractId,
                                        message = bouncedMessage,
                                    ),
                                )
                            newStmt(
                                TsaArtificialExitInst(
                                    stmt.computePhaseResult,
                                    stmt.actionPhaseResult,
                                    lastStmt.location,
                                ),
                            )
                        },
                        blockOnFalseState = {
                            newStmt(
                                TsaArtificialExitInst(
                                    stmt.computePhaseResult,
                                    stmt.actionPhaseResult,
                                    lastStmt.location,
                                ),
                            )
                        },
                    )
                } else {
                    scope.doWithState {
                        newStmt(
                            TsaArtificialExitInst(
                                stmt.computePhaseResult,
                                stmt.actionPhaseResult,
                                lastStmt.location,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun constructBouncedMessage(
        scope: TvmStepScopeManager,
        oldMessage: MessageAsStackArguments,
        oldMessageSender: ContractId,
    ): MessageAsStackArguments? {
        val msgCellAndBodySliceOrNull =
            with(ctx) {
                val builder = scope.calcOnState { allocEmptyBuilder() }
                scope.builderStoreDataBits(builder, bouncedMessageTagLong.toBv(32u))
                    ?: error("Unexpected cell overflow")
                val dataPos =
                    scope.calcOnState {
                        readSliceDataPos(oldMessage.msgBodySlice)
                    }
                val cellLength =
                    scope.calcOnState {
                        fieldManagers.cellDataLengthFieldManager.readCellDataLength(
                            this,
                            readSliceCell(oldMessage.msgBodySlice),
                        )
                    }
                val length = mkBvSubExpr(cellLength, dataPos)
                val leftLength =
                    mkIte(
                        mkBvSignedGreaterExpr(length, 256.toBv()),
                        256.toBv(ctx.sizeSort),
                        length,
                    )
                val leftData =
                    scope.slicePreloadDataBits(oldMessage.msgBodySlice, leftLength)
                        ?: return@with null
                scope.builderStoreDataBits(builder, builder, leftData, leftLength, null)
                val (bodyCell, bodySlice) =
                    scope.calcOnState {
                        val cell = builderToCell(builder)
                        val slice = allocSliceFromCell(cell)
                        cell to slice
                    }
                val destinationAddressCell =
                    scope.calcOnState {
                        getContractInfoParamOf(ADDRESS_PARAMETER_IDX, oldMessageSender).cellValue
                            ?: error("no destination :(")
                    }
                // TODO fill the ihrFee, msgValue, ... with reasonable values
                val bouncedFlags =
                    Flags(
                        intMsgInfo = 0.toBv257(),
                        ihrDisabled = 1.toBv257(),
                        bounce = 0.toBv257(),
                        bounced = 1.toBv257(),
                    )
                val dstAddressSlice = scope.calcOnState { allocSliceFromCell(destinationAddressCell) }
                val content =
                    TlbInternalMessageContent(
                        TlbCommonMessageInfo(
                            flags = bouncedFlags,
                            srcAddressSlice = oldMessage.destAddrSlice,
                            dstAddressSlice = dstAddressSlice,
                            msgValue = zeroValue,
                            ihrFee = zeroValue,
                            fwdFee = zeroValue,
                            createdLt = zeroValue,
                            createdAt = zeroValue,
                        ),
                        messageAfterCommonMsgInfo = MessageAfterCommonMsgInfo.ManuallyConstructed(bodyCell, bodySlice),
                    )
                content to dstAddressSlice
            }
        return msgCellAndBodySliceOrNull?.let { (content, dstAddressSlice) ->
            val constructedMsgCells =
                content.constructMessageCellFromContent(scope)
                    ?: error("Failed to construct bounced message of known length")
            MessageAsStackArguments(
                msgValue = oldMessage.msgValue,
                fullMsgCell = constructedMsgCells.fullMsgCell,
                msgBodySlice = constructedMsgCells.msgBodySlice,
                destAddrSlice = dstAddressSlice,
                content.commonMessageInfo,
                stateInitCell = null, // TODO: can bounced messages carry state init?
            )
        }
    }

    private fun updateTime(scope: TvmStepScopeManager): Unit? =
        with(scope.ctx) {
            val oldTime = scope.calcOnState { time }
            val newTime =
                scope.calcOnState {
                    time = generateSymbolicTime()
                    time
                }
            return scope.assert(
                mkAnd(
                    mkBvSignedGreaterOrEqualExpr(newTime, oldTime),
                    mkBvSignedGreaterOrEqualExpr(unixTimeMaxValue, newTime),
                ),
            )
        }

    private fun visitExitInst(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialExitInst,
    ) {
        val receivedMessage = scope.calcOnState { receivedMessage }
        if (receivedMessage != null) {
            updateTime(scope)
                ?: return
        }

        scope.doWithStateCtx {
            phase = TvmExitPhase(stmt.computePhaseResult, stmt.actionPhaseResult)

            if (stmt.actionPhaseResult != null) {
                val lastEvent = eventsLog.last()
                val newLastEvent =
                    lastEvent.copy(
                        actionPhaseResult = stmt.actionPhaseResult,
                    )
                eventsLog = eventsLog.removeAt(eventsLog.size - 1).add(newLastEvent)
            }

            val checkerContractId =
                contractsCode
                    .mapIndexedNotNull { index, code ->
                        if (code.isContractWithTSACheckerFunctions) index else null
                    }.singleOrNull() ?: -1
            val isReturnFromHandler = contractStack.isNotEmpty() && contractStack.last().contractId != checkerContractId
            if (isReturnFromHandler) {
                processContractStackReturn(scope, stmt)
            } else if (tvmOptions.intercontractOptions.isIntercontractEnabled && !messageQueue.isEmpty()) {
                currentPhaseBeginTime = pseudologicalTime
                processIntercontractExit(scope, stmt)
            } else {
                processContractStackReturn(scope, stmt)
            }
        }
    }

    private fun processIntercontractExit(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialExitInst,
    ) {
        scope.doWithState {
            require(!messageQueue.isEmpty()) {
                "Unexpected empty message queue during processing inter-contract exit"
            }

            val isChecker = contractsCode[currentContract].isContractWithTSACheckerFunctions

            val failure = chooseFailure(stmt.computePhaseResult, stmt.actionPhaseResult, isChecker)
            if (failure != null) {
                phase = TvmPostProcessPhase
                result = failure
                newStmt(TsaArtificialPostprocessInst(stmt.location.increment()))
                return@doWithState
            }

            contractEpilogue(isChecker)

            val (sender, receiver, message) = messageQueue.first()
            messageQueue = messageQueue.removeAt(0)
            executeContractTriggeredByMessage(sender, receiver, message)
        }
    }

    private fun TvmState.chooseFailure(
        computePhaseResult: TvmResult.TvmTerminalResult,
        actionPhaseResult: TvmResult.TvmTerminalResult?,
        isTsaChecker: Boolean,
        haveToTakeExit: Boolean = false,
    ): TvmResult.TvmTerminalResult? {
        val isExceptional = ctx.tvmOptions.stopOnFirstError && computePhaseResult.isExceptional()
        this.isExceptional = isExceptional
        if (isExceptional ||
            receivedMessage == null ||
            isTsaChecker ||
            computePhaseResult is TvmAbstractSoftFailure
        ) {
            check(actionPhaseResult == null) {
                "Action phase should have been skipped"
            }
            return computePhaseResult.takeIf { it.isExceptional() || it is TvmAbstractSoftFailure || haveToTakeExit }
        }
        if ((computePhaseResult.isExceptional() || !ctx.tvmOptions.enableOutMessageAnalysis) &&
            actionPhaseResult == null
        ) {
            return if (haveToTakeExit) computePhaseResult else null
        }
        check(actionPhaseResult != null) {
            "Action phase should not have been skipped"
        }
        return actionPhaseResult.takeIf { it.isExceptional() || it is TvmAbstractSoftFailure || haveToTakeExit }
    }

    private fun TvmState.executeContractTriggeredByMessage(
        sender: ContractSender,
        receiver: ContractId,
        message: MessageAsStackArguments,
    ) {
        val nextContractCode =
            contractsCode.getOrNull(receiver)
                ?: error("Contract with id $receiver was not found")
        intercontractPath = intercontractPath.add(receiver)

        val prevStack = stack
        // Update current contract to the next contract
        currentContract = receiver

        val wrappedMessage = ReceivedMessage.MessageFromOtherContract(sender, currentContract, message)
        val newMemory =
            initializeContractExecutionMemory(
                contractsCode,
                this,
                currentContract,
                allowInputStackValues = false,
                message = wrappedMessage,
            )
        stack = newMemory.stack
        stack.copyInputValues(prevStack)
        registersOfCurrentContract = newMemory.registers

        // TODO update balance using message value
        val balance =
            getBalance()
                ?: error("Unexpected incorrect config balance value")

        stack.addInt(balance)
        stack.addInt(message.msgValue)
        addOnStack(message.fullMsgCell, TvmCellType)
        addOnStack(message.msgBodySlice, TvmSliceType)
        receivedMessage = wrappedMessage
        phase = TvmComputePhase
        switchToFirstMethodInContract(nextContractCode, RECEIVE_INTERNAL_ID)
    }

    private fun TvmStepScopeManager.registerActionPhaseEffect(
        messagesSent: List<DispatchedMessage>,
        leftBalance: Int257Expr,
    ): Unit =
        calcOnState {
            setBalance(leftBalance)
            val messagesWithDestinations = mutableListOf<ReceivedMessage.MessageFromOtherContract>()
            val newUnprocessedMessages = mutableListOf<Pair<ContractId, DispatchedMessageContent>>()
            for ((receiverOrNull, messageContent) in messagesSent) {
                val msgStackArgs = messageContent.toStackArgs()
                if (receiverOrNull == null) {
                    newUnprocessedMessages.add(currentContract to messageContent)
                    continue
                }
                messagesWithDestinations.add(
                    ReceivedMessage.MessageFromOtherContract(
                        ContractSender(currentContract, currentEventId),
                        receiverOrNull,
                        msgStackArgs,
                    ),
                )
            }
            messageQueue = messageQueue.addAll(messagesWithDestinations)
            unprocessedMessages = unprocessedMessages.addAll(newUnprocessedMessages)
            return@calcOnState
        }

    private fun processContractStackReturn(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialExitInst,
    ) {
        scope.doWithState {
            val isTsaChecker = contractsCode[currentContract].isContractWithTSACheckerFunctions

            val failure = chooseFailure(stmt.computePhaseResult, stmt.actionPhaseResult, isTsaChecker)

            if (failure != null || contractStack.isEmpty()) {
                phase = TvmPostProcessPhase
                result = failure ?: let {
                    chooseFailure(stmt.computePhaseResult, stmt.actionPhaseResult, isTsaChecker, haveToTakeExit = true)
                        ?: error("Unexpected null")
                }
                newStmt(TsaArtificialPostprocessInst(stmt.location.increment()))
                return@doWithState
            }

            // update global c4 and c7
            if (stmt.computePhaseResult is TvmResult.TvmComputePhaseSuccess &&
                stmt.actionPhaseResult is TvmResult.TvmActionPhaseSuccess?
            ) {
                requireNotNull(lastCommitedStateOfContracts[currentContract]) {
                    "Did not find commited state of contract $currentContract"
                }
            }
            contractEpilogue(isTsaChecker)

            val stackFromOtherContract = stack

            val previousEventState = contractStack.last()
            contractStack = contractStack.removeAt(contractStack.size - 1)
            restorePreviousState(previousEventState, stackFromOtherContract)
            newStmt(previousEventState.inst)
        }
    }

    private fun TvmState.restorePreviousState(
        previousEventState: TvmEventInformation,
        stackFromOtherContract: TvmStack,
    ) {
        currentContract = previousEventState.contractId

        val prevStack = previousEventState.executionMemory.stack
        stack =
            prevStack.clone() // we should not touch stack from contractStack, as it is contained in other states
        val expectedNumberOfOutputItems = previousEventState.stackEntriesToTake
        stack.takeValuesFromOtherStack(stackFromOtherContract, expectedNumberOfOutputItems)
        registersOfCurrentContract =
            previousEventState.executionMemory.registers.clone() // like for stack, we shouldn't touch registers
        val storedC7 = checkerC7
        val isReturnToChecker = contractsCode[currentContract].isContractWithTSACheckerFunctions
        if (storedC7 != null && isReturnToChecker) {
            registersOfCurrentContract.c7 = storedC7
        }
        currentPhaseBeginTime = previousEventState.phaseBeginTime
        currentPhaseEndTime = previousEventState.phaseEndTime
        phase = TvmComputePhase
        isExceptional = previousEventState.isExceptional
        receivedMessage = previousEventState.receivedMessage
        currentComputeFeeUsed = previousEventState.computeFee
        phase = previousEventState.phase
    }
}
