package org.usvm.machine.interpreter

import org.ton.bytecode.TsaArtificialActionPhaseInst
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
import org.usvm.machine.state.TmvPhase.ACTION_PHASE
import org.usvm.machine.state.TmvPhase.COMPUTE_PHASE
import org.usvm.machine.state.TmvPhase.EXIT_PHASE
import org.usvm.machine.state.TmvPhase.TERMINATED
import org.usvm.machine.state.TvmCommitedState
import org.usvm.machine.state.TvmMethodResult
import org.usvm.machine.state.TvmMethodResult.TvmFailure
import org.usvm.machine.state.TvmMethodResult.TvmStructuralError
import org.usvm.machine.state.addInt
import org.usvm.machine.state.addOnStack
import org.usvm.machine.state.callContinuation
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.contractEpilogue
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.getBalance
import org.usvm.machine.state.initializeContractExecutionMemory
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
            is TsaArtificialExitInst -> {
                scope.consumeDefaultGas(stmt)

                visitExitInst(scope, stmt)
            }
        }
    }

    private fun visitActionPhaseInst(scope: TvmStepScopeManager, stmt: TsaArtificialActionPhaseInst) {
        scope.doWithState {
            val commitedState = lastCommitedStateOfContracts[currentContract]

            if (!analysisOfGetMethod &&
                commitedState != null &&
                ctx.tvmOptions.intercontractOptions.isIntercontractEnabled
            ) {
                phase = ACTION_PHASE

                processNewMessages(scope, commitedState)
                    ?: return@doWithState
            }

            newStmt(TsaArtificialExitInst(stmt.computePhaseResult, lastStmt.location))
        }
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

            // TODO stop at failure state or at state without commitedState
            if (analysisOfGetMethod ||
                commitedState == null ||
                result is TvmFailure && result.phase == ACTION_PHASE ||
                result is TvmStructuralError && result.phase == ACTION_PHASE
            ) {
                phase = TERMINATED
                methodResult = result
                return@doWithState
            }

            contractEpilogue()

            val (nextContract, message) = messageQueue.first()
            val nextContractCode = contractsCode.getOrNull(nextContract)
                ?: error("Contract with id $nextContract was not found")

            messageQueue = messageQueue.removeAt(0)
            intercontractPath = intercontractPath.add(nextContract)

            val prevStack = stack
            // Update current contract to the next contract
            currentContract = nextContract
            val newMemory = initializeContractExecutionMemory(contractsCode, this, currentContract, allowInputStackValues = false)
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

            lastMsgBody = message.msgBodySlice

            phase = COMPUTE_PHASE
            switchToFirstMethodInContract(nextContractCode, RECEIVE_INTERNAL_ID)
        }
    }

    private fun processNewMessages(
        scope: TvmStepScopeManager,
        commitedState: TvmCommitedState,
    ): Unit? = scope.calcOnState {
        val messageDestinations = transactionInterpreter.parseActionsToDestinations(scope, commitedState)
            ?: return@calcOnState null

        messageQueue = messageQueue.addAll(messageDestinations)
    }

    private fun processCheckerExit(scope: TvmStepScopeManager, result: TvmMethodResult) {
        scope.doWithState {
            // TODO: case of committed state of TvmFailure
            if (result !is TvmMethodResult.TvmSuccess || contractStack.isEmpty()) {
                phase = TERMINATED
                methodResult = result
                return@doWithState
            }

            val (prevContractId, prevInst, prevMem, expectedNumberOfOutputItems) = contractStack.last()

            // update global c4 and c7
            lastCommitedStateOfContracts[currentContract]
                ?: error("Did not find commited state of contract $currentContract")
            contractEpilogue()

            val stackFromOtherContract = stack

            contractStack = contractStack.removeAt(contractStack.size - 1)
            currentContract = prevContractId

            val prevStack = prevMem.stack
            stack = prevStack.clone()  // we should not touch stack from contractStack, as it is contained in other states
            stack.takeValuesFromOtherStack(stackFromOtherContract, expectedNumberOfOutputItems)
            registersOfCurrentContract = prevMem.registers

            phase = COMPUTE_PHASE
            newStmt(prevInst.nextStmt())
        }
    }
}
