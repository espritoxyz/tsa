package org.usvm.machine.interpreter

import org.ton.bytecode.TsaArtificialCheckerReturn
import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.TvmInst
import org.ton.compositeLabelOfUnknown
import org.usvm.UConcreteHeapRef
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.readField
import org.usvm.isStatic
import org.usvm.machine.TvmConcreteGeneralData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.FALSE_CONCRETE_VALUE
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.C0Register
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmContractExecutionMemory
import org.usvm.machine.state.TvmEventInformation
import org.usvm.machine.state.TvmRegisters
import org.usvm.machine.state.TvmStack
import org.usvm.machine.state.TvmStack.TvmConcreteStackEntry
import org.usvm.machine.state.TvmStack.TvmStackCellValue
import org.usvm.machine.state.TvmStack.TvmStackSliceValue
import org.usvm.machine.state.TvmStack.TvmStackTupleValueConcreteNew
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.addInt
import org.usvm.machine.state.addOnStack
import org.usvm.machine.state.calcOnStateCtx
import org.usvm.machine.state.callMethod
import org.usvm.machine.state.doWithCtx
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.getBalanceOf
import org.usvm.machine.state.initializeContractExecutionMemory
import org.usvm.machine.state.input.ReceiverInput
import org.usvm.machine.state.input.RecvExternalInput
import org.usvm.machine.state.input.RecvInternalInput
import org.usvm.machine.state.makeCellToSliceNoFork
import org.usvm.machine.state.messages.ReceivedMessage
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.switchToFirstMethodInContract
import org.usvm.machine.state.takeLastIntOrNull
import org.usvm.machine.state.takeLastIntOrThrowTypeError
import org.usvm.machine.toMethodId
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmIntegerType
import org.usvm.utils.intValueOrNull

class TsaCheckerFunctionsInterpreter(
    private val contractsCode: List<TsaContractCode>,
) {
    fun checkerReturn(
        scope: TvmStepScopeManager,
        stmt: TsaArtificialCheckerReturn,
    ) {
        scope.doWithState {
            val newStack = TvmStack(ctx, allowInputValues = false)
            stack = newStack
        }

        prepareNewStack(
            scope,
            oldStack = stmt.checkerMemorySavelist.oldStack,
            stackOperations = stmt.checkerMemorySavelist.stackOperations,
            newInput = stmt.checkerMemorySavelist.newInput,
            nextContractId = stmt.checkerMemorySavelist.nextContractId,
        )

        val registers =
            scope.calcOnState {
                registersOfCurrentContract.clone()
            }

        registers.c0 = stmt.checkerMemorySavelist.oldC0Register

        val oldMemory =
            TvmContractExecutionMemory(
                stack = stmt.checkerMemorySavelist.oldStack,
                registers = registers,
            )

        scope.calcOnState {
            finishTsaCall(
                this,
                stackOperations = stmt.checkerMemorySavelist.stackOperations,
                stmt = stmt.checkerMemorySavelist.stmt,
                oldMemory = oldMemory,
                newRegisters = stmt.checkerMemorySavelist.newRegisters,
                nextContractId = stmt.checkerMemorySavelist.nextContractId,
                nextMethodId = stmt.checkerMemorySavelist.nextMethodId,
            )
        }
    }

    /**
     * return null if operation was executed.
     * */
    fun doTSACheckerOperation(
        scope: TvmStepScopeManager,
        stmt: TvmInst,
        methodId: Int,
    ): Unit? {
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
            FORBID_FAILURES_METHOD_ID ->
                scope.doWithState {
                    allowFailures = false
                    newStmt(stmt.nextStmt())
                }

            ALLOW_FAILURES_METHOD_ID ->
                scope.doWithState {
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

            SEND_EXTERNAL_MESSAGE_ID -> {
                performRecvExternalCall(scope, stmt)
            }

            GET_BALANCE_ID -> {
                performGetBalance(scope, stmt)
            }

            else -> {
                return Unit
            }
        }

        return null
    }

    private fun performRecvInternalCall(
        scope: TvmStepScopeManager,
        stmt: TvmInst,
    ) {
        val newInputId =
            scope.calcOnState {
                getConcreteIntFromStack(parameterName = "input_id", functionName = "tsa_send_internal_message")
            }
        val nextContractId =
            scope.calcOnState {
                getConcreteIntFromStack(parameterName = "contract_id", functionName = "tsa_send_internal_message")
            }
        val nextMethodId = TvmContext.RECEIVE_INTERNAL_ID.toInt()

        performTsaCall(scope, NewReceiverInput(newInputId, ReceiverType.Internal), stmt, nextMethodId, nextContractId)
    }

    private fun performRecvExternalCall(
        scope: TvmStepScopeManager,
        stmt: TvmInst,
    ) {
        val newInputId =
            scope.calcOnState {
                getConcreteIntFromStack(parameterName = "input_id", functionName = "tsa_send_external_message")
            }
        val nextContractId =
            scope.calcOnState {
                getConcreteIntFromStack(parameterName = "contract_id", functionName = "tsa_send_external_message")
            }
        val nextMethodId = TvmContext.RECEIVE_EXTERNAL_ID.toInt()

        performTsaCall(scope, NewReceiverInput(newInputId, ReceiverType.External), stmt, nextMethodId, nextContractId)
    }

    private fun performOrdinaryTsaCall(
        scope: TvmStepScopeManager,
        stackOperations: StackOperations,
        stmt: TvmInst,
    ) {
        val nextMethodId =
            scope.calcOnState {
                getConcreteIntFromStack(parameterName = "method_id", functionName = "tsa_call")
            }
        val nextContractId =
            scope.calcOnState {
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
    ): Unit? {
        val receiverInput =
            if (stackOperations is NewReceiverInput) {
                val additionalInputs = scope.calcOnState { additionalInputs }
                additionalInputs
                    .getOrElse(stackOperations.inputId) {
                        when (stackOperations.type) {
                            ReceiverType.Internal ->
                                RecvInternalInput(
                                    scope.calcOnState { this },
                                    TvmConcreteGeneralData(),
                                    nextContractId,
                                )

                            ReceiverType.External ->
                                RecvExternalInput(
                                    scope.calcOnState { this },
                                    TvmConcreteGeneralData(),
                                    nextContractId,
                                )
                        }
                    }.also {
                        scope.doWithState {
                            this.additionalInputs = additionalInputs.put(stackOperations.inputId, it)
                        }

                        val dataCellInfoStorage = scope.calcOnState { dataCellInfoStorage }

                        val msgBodyCell =
                            scope.calcOnStateCtx {
                                memory.readField(it.msgBodySliceNonBounced, TvmContext.sliceCellField, addressSort)
                            } as UConcreteHeapRef

                        if (msgBodyCell.isStatic) {
                            dataCellInfoStorage.mapper.addLabel(
                                scope,
                                msgBodyCell,
                                compositeLabelOfUnknown,
                            ) ?: return null

                            makeCellToSliceNoFork(scope, msgBodyCell, it.msgBodySliceNonBounced)
                        }

                        when (stackOperations.type) {
                            ReceiverType.Internal -> {
                                check(it is RecvInternalInput) {
                                    "Expected input with id ${stackOperations.inputId} to be internal input. Found: $it"
                                }
                            }

                            ReceiverType.External -> {
                                check(it is RecvExternalInput) {
                                    "Expected input with id ${stackOperations.inputId} to be external input. Found: $it"
                                }
                            }
                        }
                    }
            } else {
                null
            }

        val oldStack = scope.calcOnState { stack }

        val newExecutionMemory =
            scope.calcOnState {
                initializeContractExecutionMemory(
                    contractsCode,
                    this,
                    nextContractId,
                    receiverInput?.msgValue,
                    allowInputStackValues = false,
                ).also {
                    stack = it.stack
                }
            }

        prepareNewStack(scope, oldStack, stackOperations, receiverInput, nextContractId)

        val oldMemory =
            scope.calcOnState {
                TvmContractExecutionMemory(
                    oldStack,
                    registersOfCurrentContract.clone(),
                )
            }

        val internalHandlerMethod =
            scope.calcOnState {
                contractsCode[currentContract].methods[ON_INTERNAL_MESSAGE_METHOD_ID.toMethodId()]
            }
        val externalHandlerMethod =
            scope.calcOnState {
                contractsCode[currentContract].methods[ON_EXTERNAL_MESSAGE_METHOD_ID.toMethodId()]
            }

        val handlerMethod =
            if (stackOperations is NewReceiverInput && stackOperations.type == ReceiverType.Internal) {
                internalHandlerMethod
            } else if (stackOperations is NewReceiverInput && stackOperations.type == ReceiverType.External) {
                externalHandlerMethod
            } else {
                null
            }

        if (handlerMethod != null && stackOperations is NewReceiverInput) {
            scope.doWithStateCtx {
                stack.addInt(stackOperations.inputId.toBv257())
            }
            check(receiverInput != null) {
                "Receiver input should have been calculated by now"
            }
            val savelist =
                CheckerMemorySavelist(
                    oldStack,
                    oldMemory.registers.c0,
                    receiverInput,
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
                    nextMethodId,
                )
            }
        }

        return Unit
    }

    class CheckerMemorySavelist(
        val oldStack: TvmStack,
        val oldC0Register: C0Register,
        val newInput: ReceiverInput,
        val stackOperations: NewReceiverInput,
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
        contractIdToFirstElementOfC7 =
            contractIdToFirstElementOfC7.put(
                currentContract,
                registersOfCurrentContract.c7.value[0, oldStack].cell(oldStack) as TvmStackTupleValueConcreteNew,
            )
        checkerC7 = registersOfCurrentContract.c7
        val takeFromNewStack =
            when (stackOperations) {
                is SimpleStackOperations -> stackOperations.takeFromNewStack
                is NewReceiverInput -> 0
            }

        check(currentComputeFeeUsed == null) {
            "Unexpected value of compute fee: $currentComputeFeeUsed"
        }

        contractStack =
            contractStack.add(
                TvmEventInformation(
                    currentContract,
                    stmt.nextStmt(),
                    oldMemory,
                    takeFromNewStack,
                    phaseBeginTime = currentPhaseBeginTime,
                    phaseEndTime = null,
                    receivedMessage,
                    isExceptional,
                    computeFee = null,
                    phase = phase,
                ),
            )
        currentPhaseBeginTime = pseudologicalTime
        currentContract = nextContractId
        registersOfCurrentContract = newRegisters

        val nextContractCode =
            contractsCode.getOrNull(nextContractId)
                ?: error("Contract with id $nextContractId not found")

        if (stackOperations is NewReceiverInput) {
            val input =
                state.additionalInputs[stackOperations.inputId]
                    ?: error("Input with id ${stackOperations.inputId} not found")
            receivedMessage = ReceivedMessage.InputMessage(input)
        } else {
            receivedMessage = null
        }

        switchToFirstMethodInContract(nextContractCode, nextMethodId.toMethodId())
    }

    private fun prepareNewStack(
        scope: TvmStepScopeManager,
        oldStack: TvmStack,
        stackOperations: StackOperations,
        newInput: ReceiverInput?,
        nextContractId: Int,
    ): Unit =
        with(scope.ctx) {
            when (stackOperations) {
                is SimpleStackOperations -> {
                    scope.doWithState {
                        stack.takeValuesFromOtherStack(oldStack, stackOperations.putOnNewStack)
                    }
                }

                is NewReceiverInput -> {
                    check(newInput != null) {
                        "RecvInternalInput must be generated at this point"
                    }

                    newInput.addressSlices.forEach {
                        scope.calcOnState {
                            dataCellInfoStorage.mapper.addAddressSlice(it)
                        }
                    }

                    scope.doWithState {
                        val configBalance =
                            getBalanceOf(nextContractId)
                                ?: error("Unexpected incorrect config balance value")

                        stack.addInt(configBalance)
                        stack.addInt(newInput.msgValue)
                        stack.addStackEntry(
                            TvmConcreteStackEntry(
                                TvmStackCellValue(
                                    newInput.constructFullMessage(
                                        this,
                                    ),
                                ),
                            ),
                        )
                        stack.addStackEntry(
                            TvmConcreteStackEntry(TvmStackSliceValue(newInput.msgBodySliceMaybeBounced)),
                        )
                    }
                }
            }
        }

    private fun performTsaAssert(
        scope: TvmStepScopeManager,
        stmt: TvmInst,
        invert: Boolean,
    ) {
        val flag =
            scope.takeLastIntOrThrowTypeError()
                ?: return
        val cond =
            scope.doWithCtx {
                if (invert) flag eq zeroValue else flag neq zeroValue
            }
        scope.assert(cond)
            ?: return
        scope.doWithState {
            newStmt(stmt.nextStmt())
        }
    }

    private fun performFetchValue(
        scope: TvmStepScopeManager,
        stmt: TvmInst,
    ) {
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

    private fun performMkSymbolicInt(
        scope: TvmStepScopeManager,
        stmt: TvmInst,
    ) {
        scope.doWithStateCtx {
            val isSigned = getConcreteIntFromStack(parameterName = "is_signed", functionName = "tsa_mk_int")
            val bits = getConcreteIntFromStack(parameterName = "bits", functionName = "tsa_mk_int")

            check(bits >= 0) {
                "Bits count must be non-negative, but found $bits"
            }

            val value =
                makeSymbolicPrimitive(mkBvSort(bits.toUInt())).let {
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

    private fun performGetC4(
        scope: TvmStepScopeManager,
        stmt: TvmInst,
    ) {
        scope.doWithState {
            val contractId = getConcreteIntFromStack(parameterName = "contract_id", functionName = "tsa_get_c4")

            val c4 =
                contractIdToC4Register[contractId]
                    ?: error("Contract with id $contractId not found")

            addOnStack(c4.value.value, TvmCellType)
            newStmt(stmt.nextStmt())
        }
    }

    private fun performGetBalance(
        scope: TvmStepScopeManager,
        stmt: TvmInst,
    ) {
        scope.doWithState {
            val contractId = getConcreteIntFromStack(parameterName = "contract_id", functionName = "tsa_get_c4")

            val result =
                getBalanceOf(contractId)
                    ?: error("Balance of contract $contractId not found.")

            addOnStack(result, TvmIntegerType)
            newStmt(stmt.nextStmt())
        }
    }

    private fun TvmState.getConcreteIntFromStack(
        parameterName: String,
        functionName: String,
    ): Int {
        val valueIdSymbolic = takeLastIntOrNull()
        return valueIdSymbolic?.intValueOrNull
            ?: error("Parameter $parameterName for $functionName must be concrete integer, but found $valueIdSymbolic")
    }
}
