package org.usvm.machine.interpreter

import org.ton.bytecode.TvmContractCode
import org.ton.bytecode.TvmInst
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmContractExecutionMemory
import org.usvm.machine.state.TvmContractPosition
import org.usvm.machine.state.TvmRegisters
import org.usvm.machine.state.TvmStack.TvmStackTupleValueConcreteNew
import org.usvm.machine.state.addOnStack
import org.usvm.machine.state.doWithCtx
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.initializeContractExecutionMemory
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.takeLastIntOrNull
import org.usvm.machine.state.takeLastIntOrThrowTypeError
import org.usvm.machine.toMethodId
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmIntegerType
import org.usvm.machine.types.TvmSliceType
import org.usvm.utils.intValueOrNull

class TsaCheckerFunctionsInterpreter(
    private val contractsCode: List<TvmContractCode>,
    private val transactionInterpreter: TvmTransactionInterpreter,
) {
    /**
     * return null if operation was executed.
     * */
    fun doTSACheckerOperation(scope: TvmStepScopeManager, stmt: TvmInst, methodId: Int): Unit? {
        val currentContract = scope.calcOnState { currentContract }
        val contractCode = contractsCode[currentContract]
        if (!contractCode.isContractWithTSACheckerFunctions) {
            return Unit
        }
        val stackOperationsIfTSACall = extractStackOperationsFromMethodId(methodId)
        if (stackOperationsIfTSACall != null) {
            performTsaCall(scope, stackOperationsIfTSACall, stmt)
            return null
        }
        when (methodId) {
            FORBID_FAILURES_METHOD_ID -> scope.doWithState {
                allowFailures = false
                newStmt(stmt.nextStmt())
            }
            ALLOW_FAILURES_METHOD_ID -> scope.doWithState {
                allowFailures = true
                newStmt(stmt.nextStmt())
            }
            ASSERT_METHOD_ID -> {
                performTsaAssert(scope, stmt, invert = false)
            }
            ASSERT_NOT_METHOD_ID -> {
                performTsaAssert(scope, stmt, invert = true)
            }
            PROCESS_ACTIONS_ID -> {
                processActions(scope, stmt)
            }
            else -> {
                return Unit
            }
        }
        return null
    }

    private fun performTsaCall(scope: TvmStepScopeManager, stackOperations: StackOperations, stmt: TvmInst) {
        val nextMethodId = scope.calcOnState {
            val value = takeLastIntOrNull()
            value?.intValueOrNull
                ?: error("Parameter method_id for tsa_call must be concrete integer, but found $value")
        }
        val nextContractId = scope.calcOnState {
            val value = takeLastIntOrNull()
            value?.intValueOrNull
                ?: error("Parameter contract_id for tsa_call must be concrete integer, but found $value")
        }
        scope.doWithState {
            val oldStack = stack
            val oldMemory = TvmContractExecutionMemory(
                oldStack,
                registersOfCurrentContract.c0,
                registersOfCurrentContract.c1,
                registersOfCurrentContract.c2,
                registersOfCurrentContract.c3,
                registersOfCurrentContract.c5,
                registersOfCurrentContract.c7,
            )
            // update global c4 and c7
            contractIdToC4Register = contractIdToC4Register.put(currentContract, registersOfCurrentContract.c4)
            // TODO: process possible errors
            contractIdToFirstElementOfC7 = contractIdToFirstElementOfC7.put(
                currentContract,
                registersOfCurrentContract.c7.value[0, oldStack].cell(oldStack) as TvmStackTupleValueConcreteNew
            )

            contractStack = contractStack.add(TvmContractPosition(currentContract, stmt, oldMemory, stackOperations.takeFromNewStack))
            currentContract = nextContractId

            val newExecutionMemory = initializeContractExecutionMemory(contractsCode, this, nextContractId, allowInputStackValues = false)
            stack = newExecutionMemory.stack
            stack.takeValuesFromOtherStack(oldStack, stackOperations.putOnNewStack)
            registersOfCurrentContract = TvmRegisters(
                ctx,
                c0 = newExecutionMemory.c0,
                c1 = newExecutionMemory.c1,
                c2 = newExecutionMemory.c2,
                c3 = newExecutionMemory.c3,
                c4 = contractIdToC4Register[nextContractId] ?: error("Didn't find c4 of contract $nextContractId"),
                c5 = newExecutionMemory.c5,
                c7 = newExecutionMemory.c7,
            )

            val nextContractCode = contractsCode.getOrNull(nextContractId)
                ?: error("Contract with id $nextContractId not found")
            val nextMethod = nextContractCode.methods[nextMethodId.toMethodId()]
                ?: error("Method $nextMethodId in contract $nextContractId not found.")
            newStmt(nextMethod.instList.first())
        }
    }

    private fun performTsaAssert(scope: TvmStepScopeManager, stmt: TvmInst, invert: Boolean) {
        val flag = scope.takeLastIntOrThrowTypeError()
            ?: return
        val cond = scope.doWithCtx {
            if (invert) flag eq zeroValue else flag neq zeroValue
        }
        scope.assert(cond)
            ?: return
        scope.doWithState {
            newStmt(stmt.nextStmt())
        }
    }


    private fun processActions(scope: TvmStepScopeManager, stmt: TvmInst) {
        scope.doWithState {
            val contractIdSymbolic = takeLastIntOrNull()
            val contractId = contractIdSymbolic?.intValueOrNull
                ?: error("Contract id must be concrete for processing output actions, but found $contractIdSymbolic")
            val outMessages = transactionInterpreter.executeActions(scope, contractId)
                ?: return@doWithState
            addMessagesOnTheStack(scope, outMessages)
            newStmt(stmt.nextStmt())
        }
    }

    private fun addMessagesOnTheStack(scope: TvmStepScopeManager, outMessages: List<OutMessage>) {
        if (outMessages.size > 1) {
            // TODO support multiple messages
            return
        }

        val message = outMessages.singleOrNull()
            ?: return
        scope.doWithStateCtx {
            // TODO write about org.usvm.machine.state.TvmStackUtilsKt.initializeIncomingMsgValue
            val myBalanceParameter = makeSymbolicPrimitive(int257sort)
            val msgValueParameter = makeSymbolicPrimitive(int257sort)

            val inMsgFullParameter = message.inMsgFull
            val inMsgBodyParameter = message.inMsgBody

            addOnStack(myBalanceParameter, TvmIntegerType)
            addOnStack(msgValueParameter, TvmIntegerType)
            addOnStack(inMsgFullParameter, TvmCellType)
            addOnStack(inMsgBodyParameter, TvmSliceType)
        }
    }
}
