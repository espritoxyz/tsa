package org.usvm.machine.interpreter

import org.ton.bytecode.TvmAppGasAcceptInst
import org.ton.bytecode.TvmAppGasCommitInst
import org.ton.bytecode.TvmAppGasGasconsumedInst
import org.ton.bytecode.TvmAppGasInst
import org.ton.bytecode.TvmAppGasSetgaslimitInst
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmCommitedState
import org.usvm.machine.state.TvmOutOfGas
import org.usvm.machine.state.addInt
import org.usvm.machine.state.calcConsumedGas
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.input.ReceiverInput
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.setFailure
import org.usvm.machine.state.takeLastIntOrThrowTypeError

class TvmGasInterpreter(private val ctx: TvmContext) {
    fun visitGasInst(scope: TvmStepScopeManager, stmt: TvmAppGasInst) {
        scope.consumeDefaultGas(stmt)

        when (stmt) {
            is TvmAppGasAcceptInst -> {
                scope.doWithState {
                    val receiverInput = currentInput as? ReceiverInput
                    if (receiverInput != null) {
                        acceptedInputs = acceptedInputs.add(receiverInput)
                    }
                    newStmt(stmt.nextStmt())
                }
            }
            is TvmAppGasCommitInst -> {
                scope.doWithState {
                    val registers = registersOfCurrentContract
                    val commitedState = TvmCommitedState(registers.c4, registers.c5)
                    lastCommitedStateOfContracts = lastCommitedStateOfContracts.put(currentContract, commitedState)
                    newStmt(stmt.nextStmt())
                }
            }
            is TvmAppGasGasconsumedInst -> visitGasConsumedInst(scope, stmt)
            is TvmAppGasSetgaslimitInst -> visitSetGasLimitInst(scope, stmt)
        }
    }

    private fun visitGasConsumedInst(scope: TvmStepScopeManager, stmt: TvmAppGasGasconsumedInst) {
        scope.doWithStateCtx {
            val usedGas = calcConsumedGas()

            stack.addInt(usedGas.zeroExtendToSort(int257sort))
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitSetGasLimitInst(scope: TvmStepScopeManager, stmt: TvmAppGasSetgaslimitInst) {
        with(ctx) {
            val gasLimit = (scope.takeLastIntOrThrowTypeError() ?: return).extractToSizeSort()
            val consumedGas = scope.calcOnState { calcConsumedGas() }

            scope.fork(
                mkBvSignedGreaterOrEqualExpr(gasLimit, consumedGas),
                falseStateIsExceptional = true,
                blockOnFalseState = {
                    setFailure(TvmOutOfGas(consumedGas, gasLimit))(this)
                }
            ) ?: return@with

            scope.doWithState { newStmt(stmt.nextStmt()) }
        }
    }
}
