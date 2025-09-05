package org.usvm.machine.interpreter

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
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.RECEIVE_INTERNAL_ID
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.ReceivedMessage
import org.usvm.machine.state.TvmCommitedState
import org.usvm.machine.state.TvmMethodResult
import org.usvm.machine.state.TvmMethodResult.TvmFailure
import org.usvm.machine.state.TvmPhase.ACTION_PHASE
import org.usvm.machine.state.TvmPhase.COMPUTE_PHASE
import org.usvm.machine.state.TvmPhase.EXIT_PHASE
import org.usvm.machine.state.TvmPhase.TERMINATED
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.addInt
import org.usvm.machine.state.addOnStack
import org.usvm.machine.state.allocCellFromData
import org.usvm.machine.state.callContinuation
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.contractEpilogue
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.getBalance
import org.usvm.machine.state.initializeContractExecutionMemory
import org.usvm.machine.state.isExceptional
import org.usvm.machine.state.jumpToContinuation
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.returnFromContinuation
import org.usvm.machine.state.switchToFirstMethodInContract
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmSliceType

class TvmArtificialInstInterpreter(
    val ctx: TvmContext,
    private val contractsCode: List<TsaContractCode>,
    private val transactionInterpreter: TvmTransactionInterpreter,
    private val checkerFunctionsInterpreter: TsaCheckerFunctionsInterpreter,
) {
    fun visit(scope: TvmStepScopeManager, stmt: TvmArtificialInst) {
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

    private fun visitActionPhaseInst(scope: TvmStepScopeManager, stmt: TsaArtificialActionPhaseInst) {
        val commitedState = scope.calcOnState {
            lastCommitedStateOfContracts[currentContract]
        }

        val analysisOfGetMethod = scope.calcOnState { analysisOfGetMethod }

        if (!analysisOfGetMethod && commitedState != null && ctx.tvmOptions.enableOutMessageAnalysis) {
            scope.doWithState {
                phase = ACTION_PHASE
            }

            processNewMessages(scope, commitedState)
                ?: return
        }

        scope.doWithState {
            newStmt(TsaArtificialBouncePhaseInst(stmt.computePhaseResult, lastStmt.location))
        }
    }

    private fun visitBouncePhaseInst(scope: TvmStepScopeManager, stmt: TsaArtificialBouncePhaseInst) {
        addBounceMessageIfNeeded(scope, stmt.computePhaseResult, stmt)
    }


    fun addBounceMessageIfNeeded(
        scope: TvmStepScopeManager,
        result: TvmMethodResult,
        stmt: TsaArtificialBouncePhaseInst
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
                if (result.isExceptional()) {
                    val (inputMessage, sender, _) = receivedMessage as? ReceivedMessage.MessageFromOtherContract
                        ?: run {
                            newStmt(TsaArtificialExitInst(stmt.computePhaseResult, lastStmt.location))
                            return@calcOnState
                        }
                    // if is bounceable, bounce back to sender
                    val fullMsgData = cellDataFieldManager.readCellData(scope, inputMessage.fullMsgCell)
                        ?: return@calcOnState
                    val isBounceable = mkBvAndExpr(
                        fullMsgData,
                        mkBvShiftLeftExpr(oneCellValue, 1020.toCellSort())
                    )

                    val bouncedMessageCell = constructBouncedMessage(fullMsgData, scope)
                    scope.fork(
                        isBounceable.neq(zeroCellValue), falseStateIsExceptional = true,
                        blockOnTrueState = {
                            messageQueue =
                                messageQueue.add(sender to inputMessage.copy(fullMsgCell = bouncedMessageCell))
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

    private fun TvmContext.constructBouncedMessage(
        @Suppress("Unused") // tests on bounced message structure are required
        fullMsg: UExpr<TvmContext.TvmCellDataSort>,
        scope: TvmStepScopeManager
    ): UHeapRef {
        val bounceRelatedMask = mkBvNegationExpr(mkBvShiftLeftExpr(0b0011.toCellSort(), 1019.toCellSort()))

        val bouncedBitSet = mkBvShiftLeftExpr(0b0001.toCellSort(), 1019.toCellSort())

        /**
         * When we shift the `1` by n, it gets a one-based index of (n+1)
         * Flags layout in cell
         * ```
         * |0   |ihr_dis|bounce |bounced|...|    ---  flags (see tlb)
         * |1023|1022   |1021   |1020   |        --- one-based indices in cell
         * |0   |0      |0      |1      |0|0|... --- bouncedBitSet
         * |1   |1      |0      |0      |1|1|... --- bounceRelated mask
         * ```
         */
        val msgBodyWithUpdatedFlags =
            mkBvXorExpr(
                mkBvAndExpr(fullMsg, bounceRelatedMask),
                bouncedBitSet
            )

        val updatedCell = scope.allocCellFromData(msgBodyWithUpdatedFlags, 1023.toBv())
        return updatedCell
    }

    private fun visitExitInst(scope: TvmStepScopeManager, stmt: TsaArtificialExitInst) {
        scope.doWithStateCtx {
            phase = EXIT_PHASE

            if (tvmOptions.intercontractOptions.isIntercontractEnabled && !messageQueue.isEmpty()) {
                processIntercontractExit(scope, stmt.result)
            } else {
                processCheckerExit(scope, stmt.result)
            }
        }
    }

    private fun processIntercontractExit(scope: TvmStepScopeManager, result: TvmMethodResult) {
        scope.doWithState {
            require(!messageQueue.isEmpty()) {
                "Unexpected empty message queue during processing inter-contract exit"
            }

            val commitedState = lastCommitedStateOfContracts[currentContract]
            val shouldTerminateBecauseOfFail = commitedState == null && ctx.tvmOptions.stopOnFirstError
            if (analysisOfGetMethod ||
                shouldTerminateBecauseOfFail ||
                result is TvmFailure && result.phase == ACTION_PHASE ||
                result is TvmMethodResult.TvmAbstractSoftFailure && result.phase == ACTION_PHASE
            ) {
                phase = TERMINATED
                methodResult = result
                return@doWithState
            }

            contractEpilogue()

            val (nextContract, message) = messageQueue.first()
            messageQueue = messageQueue.removeAt(0)
            executeContractTriggeredByMessage(nextContract, message)
        }
    }

    private fun TvmState.executeContractTriggeredByMessage(
        nextContract: ContractId,
        message: OutMessage
    ) {
        val nextContractCode = contractsCode.getOrNull(nextContract)
            ?: error("Contract with id $nextContract was not found")
        intercontractPath = intercontractPath.add(nextContract)

        val prevStack = stack
        // Update current contract to the next contract
        val prevContract = currentContract
        currentContract = nextContract
        val newMemory = initializeContractExecutionMemory(
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
        val balance = getBalance()
            ?: error("Unexpected incorrect config balance value")

        stack.addInt(balance)
        stack.addInt(message.msgValue)
        addOnStack(message.fullMsgCell, TvmCellType)
        addOnStack(message.msgBodySlice, TvmSliceType)
        currentInput = null
        receivedMessage = ReceivedMessage.MessageFromOtherContract(message, prevContract, currentContract)
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
            messageQueue = messageQueue.addAll(messageDestinations)
            unprocessedMessages = unprocessedMessages.addAll(newUnprocessedMessages.map { currentContract to it })
        }

        return Unit
    }

    private fun processCheckerExit(scope: TvmStepScopeManager, result: TvmMethodResult) {
        scope.doWithState {
            /**
             * if we do not enforce stopping on first error, we should not stop here and instead inspect the
             * contract stack and continue the execution of continuations found there
             */
            val shouldTerminateOfFailure = ctx.tvmOptions.stopOnFirstError && result !is TvmMethodResult.TvmSuccess
            if (shouldTerminateOfFailure || contractStack.isEmpty()) {
                phase = TERMINATED
                methodResult = result
                return@doWithState
            }

            val (prevContractId, prevInst, prevMem, expectedNumberOfOutputItems) = contractStack.last()

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
                prevStack.clone()  // we should not touch stack from contractStack, as it is contained in other states
            stack.takeValuesFromOtherStack(stackFromOtherContract, expectedNumberOfOutputItems)
            registersOfCurrentContract = prevMem.registers.clone()  // like for stack, we shouldn't touch registers

            phase = COMPUTE_PHASE
            newStmt(prevInst.nextStmt())
        }
    }
}
