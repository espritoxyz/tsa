package org.usvm.machine.interpreter

import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.TvmInst
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TvmContext.Companion.FALSE_CONCRETE_VALUE
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmContractExecutionMemory
import org.usvm.machine.state.TvmContractPosition
import org.usvm.machine.state.TvmStack.TvmStackTupleValueConcreteNew
import org.usvm.machine.state.addInt
import org.usvm.machine.state.doWithCtx
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.initializeContractExecutionMemory
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.switchToFirstMethodInContract
import org.usvm.machine.state.takeLastIntOrNull
import org.usvm.machine.state.takeLastIntOrThrowTypeError
import org.usvm.machine.toMethodId
import org.usvm.utils.intValueOrNull

class TsaCheckerFunctionsInterpreter(
    private val contractsCode: List<TsaContractCode>,
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
            FETCH_VALUE_ID -> {
                performFetchValue(scope, stmt)
            }
            MK_SYMBOLIC_INT_METHOD_ID -> {
                performMkSymbolicInt(scope, stmt)
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
                registersOfCurrentContract.clone()
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
            registersOfCurrentContract = newExecutionMemory.registers

            val nextContractCode = contractsCode.getOrNull(nextContractId)
                ?: error("Contract with id $nextContractId not found")

            switchToFirstMethodInContract(nextContractCode, nextMethodId.toMethodId())
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

    private fun performFetchValue(scope: TvmStepScopeManager, stmt: TvmInst) {
        scope.doWithState {
            val valueIdSymbolic = takeLastIntOrNull()
            val valueId  = valueIdSymbolic?.intValueOrNull
                ?: error("Parameter value_id for tsa_fetch_vaslue must be concrete integer, but found $valueIdSymbolic")
            val entry = stack.takeLastEntry()
            check(!fetchedValues.containsKey(valueId)) {
                "Value with id $valueId is already present: $fetchedValues[$valueId]"
            }
            fetchedValues = fetchedValues.put(valueId, entry)
            newStmt(stmt.nextStmt())
        }
    }

    private fun performMkSymbolicInt(scope: TvmStepScopeManager, stmt: TvmInst) {
        scope.doWithStateCtx {
            val isSigned = takeLastIntOrThrowTypeError()?.intValueOrNull
                ?: return@doWithStateCtx
            val bits = takeLastIntOrThrowTypeError()?.intValueOrNull
                ?: return@doWithStateCtx

            check(bits >= 0) {
                "Bits count must be non-negative, but found $bits"
            }

            val value = makeSymbolicPrimitive(mkBvSort(bits.toUInt())).let {
                if (isSigned == FALSE_CONCRETE_VALUE) {
                    it.zeroExtendToSort(int257sort)
                } else {
                    // every non-zero integer is considered a true value.
                    it.signExtendToSort(int257sort)
                }
            }

            stack.addInt(value)
            newStmt(stmt.nextStmt())
        }
    }
}
