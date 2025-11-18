package org.usvm.machine.interpreter

import org.ton.bytecode.ADDRESS_PARAMETER_IDX
import org.ton.bytecode.TsaArtificialActionPhaseInst
import org.ton.bytecode.TsaArtificialBouncePhaseInst
import org.ton.bytecode.TsaArtificialCheckerReturn
import org.ton.bytecode.TsaArtificialExecuteContInst
import org.ton.bytecode.TsaArtificialExitInst
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
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.RECEIVE_INTERNAL_ID
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.splitHeadTail
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmActionPhase
import org.usvm.machine.state.TvmBouncePhase
import org.usvm.machine.state.TvmCommitedState
import org.usvm.machine.state.TvmComputePhase
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
import org.usvm.machine.state.getBalance
import org.usvm.machine.state.getContractInfoParamOf
import org.usvm.machine.state.initializeContractExecutionMemory
import org.usvm.machine.state.input.RecvInternalInput
import org.usvm.machine.state.input.constructMessageFromContent
import org.usvm.machine.state.isExceptional
import org.usvm.machine.state.jumpToContinuation
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.messages.ContractSender
import org.usvm.machine.state.messages.MessageAsStackArguments
import org.usvm.machine.state.messages.MessageSource
import org.usvm.machine.state.messages.ReceivedMessage
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.readSliceCell
import org.usvm.machine.state.readSliceDataPos
import org.usvm.machine.state.returnFromContinuation
import org.usvm.machine.state.setBalance
import org.usvm.machine.state.slicePreloadDataBits
import org.usvm.machine.state.switchToFirstMethodInContract
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmSliceType
import org.usvm.sizeSort

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

            is TsaArtificialActionPhaseInst -> {
                scope.consumeDefaultGas(stmt)

                visitActionPhaseInst(scope, stmt)
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

    private fun TvmState.doCallOnOutMessageIfRequired(stmt: TsaArtificialOnOutMessageHandlerCallInst): Unit? {
        if (contractsCode[currentContract].isContractWithTSACheckerFunctions) {
            return null
        }
        val (head, tail) =
            stmt.sentMessages.splitHeadTail() ?: return null
        val nextInst = stmt.copy(sentMessages = tail)
        val currentContractToPush = currentContract
        val pushArgsOnStack: TvmState.() -> Unit = {
            with(ctx) {
                stack.addCell(head.message.fullMsgCell)
                stack.addSlice(head.message.msgBodySlice)
                stack.addInt((head.receiver ?: -1).toBv257()) // receiver
                stack.addInt(currentContractToPush.toBv257()) // sender
            }
        }

        return callCheckerMethodIfExists(
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
        scope.doWithState {
            val wasCalled = doCallOnOutMessageIfRequired(stmt) != null
            if (!wasCalled) {
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

    private fun TvmState.doCallOnComputeExitIfNecessary(stmt: TsaArtificialOnComputePhaseExitInst): Unit? {
        if (contractsCode[currentContract].isContractWithTSACheckerFunctions) {
            return null
        }
        val correspondingSymbol =
            currentComputeFeeUsed
                ?: error("Current compute_fee should be non-null here")
        val caleeContract = currentContract
        val pushArgsOnStack: TvmState.() -> Unit = {
            with(ctx) {
                stack.addInt(correspondingSymbol)
                stack.addInt(caleeContract.toBv257())
            }
        }
        val nextInst = TsaArtificialActionPhaseInst(stmt.computePhaseResult, stmt.location)
        return callCheckerMethodIfExists(
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
            val shouldNotCallExitHandler = scope.ctx.tvmOptions.stopOnFirstError && isExceptional || isTsaChecker
            if (!shouldNotCallExitHandler) {
                // A temporary solution. The more suitable solution would calculate
                // `computeFeeUsed` as the state instructions are executed (possibly with some helper
                // structures). When this happends, this comment and the line below must be deleted
                currentComputeFeeUsed = makeSymbolicPrimitive(ctx.int257sort)
                currentPhaseEndTime = pseudologicalTime

                registerEventIfNeeded(stmt.computePhaseResult)

                val wasCalled = doCallOnComputeExitIfNecessary(stmt) != null
                if (!wasCalled) {
                    newStmt(TsaArtificialActionPhaseInst(stmt.computePhaseResult, lastStmt.location))
                }
            } else {
                newStmt(TsaArtificialActionPhaseInst(stmt.computePhaseResult, lastStmt.location))
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
                    ),
                )
        }
    }

    private fun visitActionPhaseInst(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialActionPhaseInst,
    ) {
        val isTsaChecker = scope.calcOnState { contractsCode[currentContract].isContractWithTSACheckerFunctions }

        val commitedState =
            scope.calcOnState {
                lastCommitedStateOfContracts[currentContract]
            }

        // temporarily remove isExceptional mark for further processing
        val oldIsExceptional = scope.calcOnState { isExceptional }
        scope.doWithState {
            isExceptional = false
        }

        val analyzingReceiver = scope.calcOnState { receivedMessage != null }
        if (analyzingReceiver && commitedState != null && ctx.tvmOptions.enableOutMessageAnalysis && !isTsaChecker) {
            scope.doWithState {
                phase = TvmActionPhase(stmt.computePhaseResult)
            }

            processNewMessages(scope, commitedState, stmt.computePhaseResult) { result, messages ->
                val sentMessages =
                    messages.map { (receiver, outMessage) ->
                        TsaArtificialOnOutMessageHandlerCallInst.SentMessage(outMessage.content, receiver)
                    }
                doWithState {
                    isExceptional = isExceptional || oldIsExceptional
                    newStmt(
                        TsaArtificialOnOutMessageHandlerCallInst(
                            computePhaseResult = stmt.computePhaseResult,
                            actionPhaseResult = result,
                            lastStmt.location,
                            sentMessages,
                        ),
                    )
                }
            }
        } else {
            scope.doWithState {
                isExceptional = isExceptional || oldIsExceptional
                newStmt(
                    TsaArtificialOnOutMessageHandlerCallInst(
                        computePhaseResult = stmt.computePhaseResult,
                        actionPhaseResult = null,
                        lastStmt.location,
                        sentMessages = emptyList(),
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
                val bodySlice =
                    scope.calcOnState {
                        allocSliceFromCell(builderToCell(builder))
                    }
                val destinationAddressCell =
                    scope.calcOnState {
                        getContractInfoParamOf(ADDRESS_PARAMETER_IDX, oldMessageSender).cellValue
                            ?: error("no destination :(")
                    }
                // TODO fill the ihrFee, msgValue, ... with reasonable values
                val bouncedFlags =
                    RecvInternalInput.Flags(
                        intMsgInfo = 0.toBv257(),
                        ihrDisabled = 1.toBv257(),
                        bounce = 0.toBv257(),
                        bounced = 1.toBv257(),
                    )
                val dstAddressSlice = scope.calcOnState { allocSliceFromCell(destinationAddressCell) }
                val content =
                    RecvInternalInput.TlbMessageContent(
                        flags = bouncedFlags,
                        srcAddressSlice = oldMessage.destAddrSlice,
                        dstAddressSlice = dstAddressSlice,
                        msgValue = zeroValue,
                        ihrFee = zeroValue,
                        fwdFee = zeroValue,
                        createdLt = zeroValue,
                        createdAt = zeroValue,
                        bodyDataSlice = bodySlice,
                    )
                Triple(constructMessageFromContent(scope.calcOnState { this }, content), bodySlice, dstAddressSlice)
            }
        return msgCellAndBodySliceOrNull?.let { (msgCell, bodySlice, dstAddressSlice) ->
            MessageAsStackArguments(
                msgValue = oldMessage.msgValue,
                fullMsgCell = msgCell,
                msgBodySlice = bodySlice,
                destAddrSlice = dstAddressSlice,
                source = MessageSource.Bounced,
                fwdFee = ctx.zeroValue,
            )
        }
    }

    private fun visitExitInst(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialExitInst,
    ) {
        scope.doWithStateCtx {
            phase = TvmExitPhase(stmt.computePhaseResult, stmt.actionPhaseResult)

            if (stmt.actionPhaseResult != null) {
                val lastEvent = eventsLog.last()
                lastEvent.actionPhaseResult = stmt.actionPhaseResult
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
            executeContractTriggeredByMessage(receiver, message, sender)
        }
    }

    private fun TvmState.chooseFailure(
        computePhaseResult: TvmResult.TvmTerminalResult,
        actionPhaseResult: TvmResult.TvmTerminalResult?,
        isTsaChecker: Boolean,
    ): TvmResult.TvmTerminalResult? {
        if (ctx.tvmOptions.stopOnFirstError ||
            receivedMessage == null ||
            isTsaChecker ||
            computePhaseResult is TvmAbstractSoftFailure
        ) {
            check(actionPhaseResult == null) {
                "Action phase should have been skipped"
            }
            return computePhaseResult.takeIf { it.isExceptional() }
        }
        check(actionPhaseResult != null) {
            "Action phase should not have been skipped"
        }
        return actionPhaseResult.takeIf { it.isExceptional() }
    }

    private fun TvmState.executeContractTriggeredByMessage(
        receiver: ContractId,
        message: MessageAsStackArguments,
        sender: ContractSender,
    ) {
        val nextContractCode =
            contractsCode.getOrNull(receiver)
                ?: error("Contract with id $receiver was not found")
        intercontractPath = intercontractPath.add(receiver)

        val prevStack = stack
        // Update current contract to the next contract
        currentContract = receiver
        val newMemory =
            initializeContractExecutionMemory(
                contractsCode,
                this,
                currentContract,
                allowInputStackValues = false,
                newMsgValue = message.msgValue,
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
        receivedMessage = ReceivedMessage.MessageFromOtherContract(sender, currentContract, message)
        phase = TvmComputePhase
        switchToFirstMethodInContract(nextContractCode, RECEIVE_INTERNAL_ID)
    }

    private fun processNewMessages(
        scope: TvmStepScopeManager,
        commitedState: TvmCommitedState,
        computePhaseResult: TvmResult.TvmTerminalResult,
        restActions: TvmStepScopeManager.(
            TvmResult.TvmTerminalResult,
            List<TvmTransactionInterpreter.MessageWithMaybeReceiver>,
        ) -> Unit,
    ) {
        transactionInterpreter.parseActionsToDestinations(
            scope,
            commitedState,
        ) {
            transactionInterpreter.handleMessages(this, it.orderedMessages) { actionsHandlingResult ->
                when (actionsHandlingResult) {
                    is ActionHandlingResult.Success -> {
                        this.calcOnState { registerActionPhaseEffect(actionsHandlingResult) }
                        this.restActions(
                            TvmResult.TvmActionPhaseSuccess(computePhaseResult),
                            actionsHandlingResult.messagesSent,
                        )
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
                            newStmt(TsaArtificialExitInst(computePhaseResult, failure, lastStmt.location))
                        }
                    }

                    is ActionHandlingResult.SoftFailure -> {
                        this.calcOnState {
                            val failure =
                                TvmResult.TvmSoftFailure(
                                    actionsHandlingResult.failure,
                                    phase,
                                )
                            newStmt(TsaArtificialExitInst(computePhaseResult, failure, lastStmt.location))
                        }
                    }
                }
            }
        }
    }

    private fun TvmState.registerActionPhaseEffect(finalMessageHandlingState: ActionHandlingResult.Success) {
        setBalance(finalMessageHandlingState.balanceLeft)
        val messagesSent = finalMessageHandlingState.messagesSent
        val messagesWithDestinations =
            messagesSent.mapNotNull { (receiverOrNull, message) ->
                receiverOrNull?.let { receiver ->
                    ReceivedMessage.MessageFromOtherContract(
                        ContractSender(currentContract, currentEventId),
                        receiver,
                        message.content,
                    )
                }
            }
        val newUnprocessedMessages =
            messagesSent.mapNotNull { (receiverOrNull, message) ->
                if (receiverOrNull == null) currentContract to message else null
            }
        messageQueue = messageQueue.addAll(messagesWithDestinations)
        unprocessedMessages = unprocessedMessages.addAll(newUnprocessedMessages)
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
                    check(stmt.actionPhaseResult == null) {
                        "No action phase was expected"
                    }
                    stmt.computePhaseResult
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
    }
}
