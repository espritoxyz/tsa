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
import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.TvmArtificialInst
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.RECEIVE_INTERNAL_ID
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmCommitedState
import org.usvm.machine.state.TvmMessageDrivenContractExecutionEntry
import org.usvm.machine.state.TvmMethodResult
import org.usvm.machine.state.TvmMethodResult.TvmAbstractSoftFailure
import org.usvm.machine.state.TvmMethodResult.TvmFailure
import org.usvm.machine.state.TvmPhase
import org.usvm.machine.state.TvmPhase.ACTION_PHASE
import org.usvm.machine.state.TvmPhase.COMPUTE_PHASE
import org.usvm.machine.state.TvmPhase.EXIT_PHASE
import org.usvm.machine.state.TvmPhase.TERMINATED
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.addInt
import org.usvm.machine.state.addOnStack
import org.usvm.machine.state.allocEmptyBuilder
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.builderStoreDataBits
import org.usvm.machine.state.builderToCell
import org.usvm.machine.state.callContinuation
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.contractEpilogue
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.getBalance
import org.usvm.machine.state.getContractInfoParamOf
import org.usvm.machine.state.initializeContractExecutionMemory
import org.usvm.machine.state.input.RecvInternalInput
import org.usvm.machine.state.input.constructMessageFromContent
import org.usvm.machine.state.jumpToContinuation
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.messages.ContractSender
import org.usvm.machine.state.messages.OutMessage
import org.usvm.machine.state.messages.ReceivedMessage
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.readSliceCell
import org.usvm.machine.state.readSliceDataPos
import org.usvm.machine.state.returnFromContinuation
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
        }
    }

    private fun visitActionPhaseInst(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialActionPhaseInst,
    ) {
        val commitedState =
            scope.calcOnState {
                lastCommitedStateOfContracts[currentContract]
            }

        val analysisOfGetMethod = scope.calcOnState { analysisOfGetMethod }

        if (!analysisOfGetMethod && commitedState != null && ctx.tvmOptions.enableOutMessageAnalysis) {
            scope.doWithState {
                phase = ACTION_PHASE
            }

            processNewMessages(scope, commitedState)
                ?: return run {
                    scope.doWithState {
                        newStmt(TsaArtificialBouncePhaseInst(stmt.computePhaseResult, lastStmt.location))
                    }
                }
        }

        scope.doWithState {
            newStmt(TsaArtificialBouncePhaseInst(stmt.computePhaseResult, lastStmt.location))
        }
    }

    private fun visitBouncePhaseInst(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialBouncePhaseInst,
    ) {
        scope.calcOnState { phase = TvmPhase.BOUNCE_PHASE }
        addBounceMessageIfNeeded(scope, stmt.computePhaseResult, stmt)
    }

    fun addBounceMessageIfNeeded(
        scope: TvmStepScopeManager,
        result: TvmMethodResult,
        stmt: TsaArtificialBouncePhaseInst,
    ) {
        if (scope.ctx.tvmOptions.stopOnFirstError) {
            // sending bounced messages is only considered when the message handling ended with an exception
            // if we stop on the first error, the potential bouncing won't be considered
            scope.doWithState {
                newStmt(TsaArtificialExitInst(stmt.computePhaseResult, lastStmt.location))
            }
            return
        }
        scope.calcOnState {
            with(ctx) {
                if (result is TvmFailure) {
                    val (sender, _, receivedMsgData) =
                        receivedMessage as? ReceivedMessage.MessageFromOtherContract
                            ?: run {
                                newStmt(TsaArtificialExitInst(stmt.computePhaseResult, lastStmt.location))
                                return@calcOnState
                            }
                    // if is bounceable, bounce back to sender
                    val fullMsgData =
                        fieldManagers.cellDataFieldManager.readCellData(scope, receivedMsgData.fullMsgCell)
                            ?: return@calcOnState
                    val isBounceable =
                        mkBvAndExpr(
                            fullMsgData,
                            mkBvShiftLeftExpr(oneCellValue, 1020.toCellSort())
                        )

                    val bouncedMessage =
                        constructBouncedMessage(scope, receivedMsgData, sender.contractId)
                            ?: return@with
                    scope.fork(
                        isBounceable.neq(zeroCellValue),
                        falseStateIsExceptional = true,
                        blockOnTrueState = {
                            messageQueue =
                                messageQueue.add(
                                    ReceivedMessage.MessageFromOtherContract(
                                        sender = ContractSender(currentContract, currentEventId),
                                        receiver = sender.contractId,
                                        message = bouncedMessage
                                    )
                                )
                            newStmt(TsaArtificialExitInst(stmt.computePhaseResult, lastStmt.location))
                        },
                        blockOnFalseState = {
                            newStmt(TsaArtificialExitInst(stmt.computePhaseResult, lastStmt.location))
                        }
                    )
                } else {
                    scope.doWithState {
                        newStmt(TsaArtificialExitInst(stmt.computePhaseResult, lastStmt.location))
                    }
                }
            }
        }
    }

    private fun constructBouncedMessage(
        scope: TvmStepScopeManager,
        oldMessage: OutMessage,
        sender: ContractId,
    ): OutMessage? {
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
                            readSliceCell(oldMessage.msgBodySlice)
                        )
                    }
                val length = mkBvSubExpr(cellLength, dataPos)
                val leftLength =
                    mkIte(
                        mkBvSignedGreaterExpr(length, 256.toBv()),
                        256.toBv(ctx.sizeSort),
                        length
                    )
                val leftData =
                    scope.slicePreloadDataBits(oldMessage.msgBodySlice, leftLength)
                        ?: return@with null
                scope.builderStoreDataBits(builder, builder, leftData, leftLength, null)
                val bodySlice =
                    scope.calcOnState {
                        allocSliceFromCell(builderToCell(builder))
                    }
                val destinationCell =
                    scope.calcOnState {
                        getContractInfoParamOf(ADDRESS_PARAMETER_IDX, sender).cellValue ?: error("no destination :(")
                    }
                // TODO fill the ihrFee, msgValue, ... with reasonable values
                val content =
                    RecvInternalInput.MessageContent(
                        flags = 0b0101.toBv257(),
                        srcAddressSlice = oldMessage.destAddrSlice,
                        dstAddressSlice = scope.calcOnState { allocSliceFromCell(destinationCell) },
                        msgValue = zeroValue,
                        ihrFee = zeroValue,
                        fwdFee = zeroValue,
                        createdLt = zeroValue,
                        createdAt = zeroValue,
                        bodyDataSlice = bodySlice
                    )
                constructMessageFromContent(scope.calcOnState { this }, content) to bodySlice
            }
        return msgCellAndBodySliceOrNull?.let { (msgCell, bodySlice) ->
            OutMessage(
                msgValue = oldMessage.msgValue,
                fullMsgCell = msgCell,
                msgBodySlice = bodySlice,
                destAddrSlice = oldMessage.destAddrSlice
            )
        }
    }

    private fun visitExitInst(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialExitInst,
    ) {
        scope.doWithStateCtx {
            phase = EXIT_PHASE
            receivedMessage?.let { receivedMessage ->
                val methodResult = stmt.result
                eventsLog =
                    eventsLog.add(
                        TvmMessageDrivenContractExecutionEntry(
                            id = currentPhaseBeginTime,
                            executionBegin = currentPhaseBeginTime,
                            executionEnd = pseudologicalTime,
                            contractId = currentContract,
                            incomingMessage = receivedMessage,
                            methodResult = methodResult
                        )
                    )
            }
            if (tvmOptions.intercontractOptions.isIntercontractEnabled && !messageQueue.isEmpty()) {
                currentPhaseBeginTime = pseudologicalTime
                processIntercontractExit(scope, stmt.result)
            } else {
                // currentPhaseBegin will be lifted from contract stack
                processCheckerExit(scope, stmt.result)
            }
        }
    }

    private fun processIntercontractExit(
        scope: TvmStepScopeManager,
        result: TvmMethodResult,
    ) {
        scope.doWithState {
            require(!messageQueue.isEmpty()) {
                "Unexpected empty message queue during processing inter-contract exit"
            }

            val commitedState = lastCommitedStateOfContracts[currentContract]
            val shouldTerminateBecauseOfFail = commitedState == null && ctx.tvmOptions.stopOnFirstError

            val failureInActionPhase = result is TvmFailure && result.phase == ACTION_PHASE
            val softFailureInActionPhase = result is TvmAbstractSoftFailure && result.phase == ACTION_PHASE
            if (analysisOfGetMethod ||
                shouldTerminateBecauseOfFail ||
                failureInActionPhase ||
                softFailureInActionPhase
            ) {
                phase = TERMINATED
                methodResult = result
                return@doWithState
            }

            contractEpilogue()

            val (sender, receiver, message) = messageQueue.first()
            messageQueue = messageQueue.removeAt(0)
            executeContractTriggeredByMessage(receiver, message, sender)
        }
    }

    private fun TvmState.executeContractTriggeredByMessage(
        receiver: ContractId,
        message: OutMessage,
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
                newMsgValue = message.msgValue
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
        currentInput = null
        receivedMessage = ReceivedMessage.MessageFromOtherContract(sender, currentContract, message)
        phase = COMPUTE_PHASE
        switchToFirstMethodInContract(nextContractCode, RECEIVE_INTERNAL_ID)
    }

    private fun processNewMessages(
        scope: TvmStepScopeManager,
        commitedState: TvmCommitedState,
    ): Unit? {
        val (newUnprocessedMessages, messageDestinations) =
            transactionInterpreter.parseActionsToDestinations(scope, commitedState)
                ?: return null

        scope.doWithState {
            messageQueue =
                messageQueue.addAll(
                    messageDestinations.map { (receiver, message) ->
                        ReceivedMessage.MessageFromOtherContract(
                            ContractSender(currentContract, currentEventId),
                            receiver,
                            message
                        )
                    }
                )
            unprocessedMessages = unprocessedMessages.addAll(newUnprocessedMessages.map { currentContract to it })
        }

        return Unit
    }

    private fun processCheckerExit(
        scope: TvmStepScopeManager,
        result: TvmMethodResult,
    ) {
        scope.doWithState {
            /**
             * if we do not enforce stopping on first error, we should not stop here and instead inspect the
             * contract stack and continue the execution of continuations found there
             */
            val shouldTerminateOfFailure =
                (ctx.tvmOptions.stopOnFirstError && result is TvmFailure) ||
                    result is TvmAbstractSoftFailure
            if (shouldTerminateOfFailure || contractStack.isEmpty()) {
                phase = TERMINATED
                methodResult = result
                return@doWithState
            }

            val (prevContractId, prevInst, prevMem, expectedNumberOfOutputItems, eventId, receivedMessage) =
                contractStack.last()
            this.receivedMessage = receivedMessage

            // update global c4 and c7
            if (result is TvmMethodResult.TvmSuccess) {
                requireNotNull(lastCommitedStateOfContracts[currentContract]) {
                    "Did not find commited state of contract $currentContract"
                }
            }
            contractEpilogue()

            val stackFromOtherContract = stack

            contractStack = contractStack.removeAt(contractStack.size - 1)
            currentContract = prevContractId

            val prevStack = prevMem.stack
            stack =
                prevStack.clone() // we should not touch stack from contractStack, as it is contained in other states
            stack.takeValuesFromOtherStack(stackFromOtherContract, expectedNumberOfOutputItems)
            registersOfCurrentContract = prevMem.registers.clone() // like for stack, we shouldn't touch registers
            currentPhaseBeginTime = eventId
            phase = COMPUTE_PHASE
            newStmt(prevInst.nextStmt())
        }
    }
}
