package org.usvm.machine.interpreter

import org.ton.bytecode.TsaArtificialCheckerReturn
import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.TvmInst
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TvmConcreteGeneralData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.FALSE_CONCRETE_VALUE
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmContractExecutionMemory
import org.usvm.machine.state.TvmContractPosition
import org.usvm.machine.state.TvmRegisters
import org.usvm.machine.state.TvmStack
import org.usvm.machine.state.TvmStack.TvmConcreteStackEntry
import org.usvm.machine.state.TvmStack.TvmStackCellValue
import org.usvm.machine.state.TvmStack.TvmStackSliceValue
import org.usvm.machine.state.TvmStack.TvmStackTupleValueConcreteNew
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.addInt
import org.usvm.machine.state.addOnStack
import org.usvm.machine.state.callMethod
import org.usvm.machine.state.doWithCtx
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.getBalanceOf
import org.usvm.machine.state.initializeContractExecutionMemory
import org.usvm.machine.state.input.RecvInternalInput
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.switchToFirstMethodInContract
import org.usvm.machine.state.takeLastIntOrNull
import org.usvm.machine.state.takeLastIntOrThrowTypeError
import org.usvm.machine.toMethodId
import org.usvm.machine.types.TvmCellType
import org.usvm.utils.intValueOrNull

class TsaCheckerFunctionsInterpreter(
    private val contractsCode: List<TsaContractCode>,
) {
    fun checkerReturn(scope: TvmStepScopeManager, stmt: TsaArtificialCheckerReturn) {
        scope.doWithState {
            val newStack = TvmStack(ctx, allowInputValues = false)
            stack = newStack
        }

        prepareNewStack(
            scope,
            oldStack = stmt.checkerMemorySavelist.oldMemory.stack,
            stackOperations = stmt.checkerMemorySavelist.stackOperations,
            recvInternalInput = stmt.checkerMemorySavelist.recvInternalInput,
            nextContractId = stmt.checkerMemorySavelist.nextContractId,
        ) ?: return

        scope.calcOnState {
            finishTsaCall(
                this,
                stackOperations = stmt.checkerMemorySavelist.stackOperations,
                stmt = stmt.checkerMemorySavelist.stmt,
                oldMemory = stmt.checkerMemorySavelist.oldMemory,
                newRegisters = stmt.checkerMemorySavelist.newRegisters,
                nextContractId = stmt.checkerMemorySavelist.nextContractId,
                nextMethodId = stmt.checkerMemorySavelist.nextMethodId,
            )
        }
    }

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
            performOrdinaryTsaCall(scope, stackOperationsIfTSACall, stmt)
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

            SEND_INTERNAL_MESSAGE_ID -> {
                performRecvInternalCall(scope, stmt)
            }

            MK_SYMBOLIC_INT_METHOD_ID -> {
                performMkSymbolicInt(scope, stmt)
            }

            GET_C4_METHOD_ID -> {
                performGetC4(scope, stmt)
            }

            else -> {
                return Unit
            }
        }
        return null
    }

    private fun performRecvInternalCall(scope: TvmStepScopeManager, stmt: TvmInst) {
        val newInputId = scope.calcOnState {
            getConcreteIntFromStack(parameterName = "input_id", functionName = "tsa_send_internal_message")
        }
        val nextContractId = scope.calcOnState {
            getConcreteIntFromStack(parameterName = "contract_id", functionName = "tsa_send_internal_message")
        }
        val nextMethodId = TvmContext.RECEIVE_INTERNAL_ID.toInt()

        performTsaCall(scope, NewRecvInternalInput(newInputId), stmt, nextMethodId, nextContractId)
    }

    private fun performOrdinaryTsaCall(scope: TvmStepScopeManager, stackOperations: StackOperations, stmt: TvmInst) {
        val nextMethodId = scope.calcOnState {
            getConcreteIntFromStack(parameterName = "method_id", functionName = "tsa_call")
        }
        val nextContractId = scope.calcOnState {
            getConcreteIntFromStack(parameterName = "contract_id", functionName = "tsa_call")
        }

        performTsaCall(scope, stackOperations, stmt, nextMethodId, nextContractId)
    }

    private fun performTsaCall(
        scope: TvmStepScopeManager,
        stackOperations: StackOperations,
        stmt: TvmInst,
        nextMethodId: Int,
        nextContractId: Int,
    ) {
        val recvInternalInput = scope.calcOnState {
            if (stackOperations is NewRecvInternalInput) {
                additionalInputs.getOrElse(stackOperations.inputId) {
                    RecvInternalInput(this, TvmConcreteGeneralData(), nextContractId)
                }.also {
                    additionalInputs = additionalInputs.put(stackOperations.inputId, it)
                }
            } else {
                null
            }
        }

        val oldStack = scope.calcOnState { stack }

        val newExecutionMemory = scope.calcOnState {
            initializeContractExecutionMemory(
                contractsCode,
                this,
                nextContractId,
                recvInternalInput?.msgValue,
                allowInputStackValues = false,
            ).also {
                stack = it.stack
            }
        }

        prepareNewStack(scope, oldStack, stackOperations, recvInternalInput, nextContractId)
            ?: return

        val oldMemory = scope.calcOnState {
            TvmContractExecutionMemory(
                oldStack,
                registersOfCurrentContract.clone()
            )
        }

        val handlerMethod = scope.calcOnState {
            contractsCode[currentContract].methods[ON_INTERNAL_MESSAGE_METHOD_ID.toMethodId()]
        }

        if (handlerMethod != null && stackOperations is NewRecvInternalInput) {
            scope.doWithStateCtx {
                stack.addInt(stackOperations.inputId.toBv257())
            }
            check(recvInternalInput != null) {
                "RecvInternalInput should have been calculated by now"
            }
            val savelist = CheckerMemorySavelist(
                oldMemory,
                recvInternalInput,
                stackOperations,
                newExecutionMemory.registers,
                nextContractId,
                nextMethodId,
                stmt,
            )
            scope.callMethod(stmt, handlerMethod, checkerMemorySavelist = savelist)
        } else {
            scope.calcOnState {
                finishTsaCall(
                    this,
                    stackOperations,
                    stmt,
                    oldMemory,
                    newExecutionMemory.registers,
                    nextContractId,
                    nextMethodId
                )
            }
        }
    }

    class CheckerMemorySavelist(
        val oldMemory: TvmContractExecutionMemory,
        val recvInternalInput: RecvInternalInput,
        val stackOperations: NewRecvInternalInput,
        val newRegisters: TvmRegisters,
        val nextContractId: ContractId,
        val nextMethodId: Int,
        val stmt: TvmInst,
    )

    private fun finishTsaCall(
        state: TvmState,
        stackOperations: StackOperations,
        stmt: TvmInst,
        oldMemory: TvmContractExecutionMemory,
        newRegisters: TvmRegisters,
        nextContractId: ContractId,
        nextMethodId: Int,
    ) = with(state) {
        val oldStack = oldMemory.stack

        // update global c4 and c7
        contractIdToC4Register = contractIdToC4Register.put(currentContract, registersOfCurrentContract.c4)
        // TODO: process possible errors
        contractIdToFirstElementOfC7 = contractIdToFirstElementOfC7.put(
            currentContract,
            registersOfCurrentContract.c7.value[0, oldStack].cell(oldStack) as TvmStackTupleValueConcreteNew
        )

        val takeFromNewStack = when (stackOperations) {
            is SimpleStackOperations -> stackOperations.takeFromNewStack
            is NewRecvInternalInput -> 0
        }

        contractStack = contractStack.add(TvmContractPosition(currentContract, stmt, oldMemory, takeFromNewStack))
        currentContract = nextContractId
        registersOfCurrentContract = newRegisters

        val nextContractCode = contractsCode.getOrNull(nextContractId)
            ?: error("Contract with id $nextContractId not found")

        switchToFirstMethodInContract(nextContractCode, nextMethodId.toMethodId())
    }

    private fun prepareNewStack(
        scope: TvmStepScopeManager,
        oldStack: TvmStack,
        stackOperations: StackOperations,
        recvInternalInput: RecvInternalInput?,
        nextContractId: Int,
    ): Unit? = with(scope.ctx) {
        when (stackOperations) {
            is SimpleStackOperations -> {
                scope.doWithState {
                    stack.takeValuesFromOtherStack(oldStack, stackOperations.putOnNewStack)
                }
            }

            is NewRecvInternalInput -> {
                check(recvInternalInput != null) {
                    "RecvInternalInput must be generated at this point"
                }

                recvInternalInput.getAddressSlices().forEach {
                    scope.calcOnState {
                        dataCellInfoStorage.mapper.addAddressSlice(it)
                    }
                }
                val addressConstraint = scope.calcOnState {
                    dataCellInfoStorage.mapper.addAddressSliceAndGenerateConstraint(
                        this,
                        recvInternalInput.srcAddressSlice
                    )
                }

                scope.assert(addressConstraint)
                    ?: return@with null

                scope.doWithState {
                    val configBalance = getBalanceOf(nextContractId)
                        ?: error("Unexpected incorrect config balance value")

                    stack.addInt(configBalance)
                    stack.addInt(recvInternalInput.msgValue)
                    stack.addStackEntry(
                        TvmConcreteStackEntry(
                            TvmStackCellValue(
                                recvInternalInput.constructFullMessage(
                                    this
                                )
                            )
                        )
                    )
                    stack.addStackEntry(TvmConcreteStackEntry(TvmStackSliceValue(recvInternalInput.msgBodySliceMaybeBounced)))
                }
            }
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
            val valueId = getConcreteIntFromStack(parameterName = "value_id", functionName = "tsa_fetch_value")
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
            val isSigned = getConcreteIntFromStack(parameterName = "is_signed", functionName = "tsa_mk_int")
            val bits = getConcreteIntFromStack(parameterName = "bits", functionName = "tsa_mk_int")

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

    private fun performGetC4(scope: TvmStepScopeManager, stmt: TvmInst) {
        scope.doWithState {
            val contractId = getConcreteIntFromStack(parameterName = "contract_id", functionName = "tsa_get_c4")

            val c4 = contractIdToC4Register[contractId]
                ?: error("Contract with id $contractId not found")

            addOnStack(c4.value.value, TvmCellType)
            newStmt(stmt.nextStmt())
        }
    }

    private fun TvmState.getConcreteIntFromStack(parameterName: String, functionName: String): Int {
        val valueIdSymbolic = takeLastIntOrNull()
        return valueIdSymbolic?.intValueOrNull
            ?: error("Parameter $parameterName for $functionName must be concrete integer, but found $valueIdSymbolic")
    }
}
