package org.usvm.machine.interpreter

import io.ksmt.expr.KInterpretedValue
import io.ksmt.utils.BvUtils.bvMaxValueSigned
import io.ksmt.utils.BvUtils.bvMinValueSigned
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import mu.KLogging
import org.ton.TlbBasicMsgAddrLabel
import org.ton.TlbFullMsgAddrLabel
import org.ton.TvmInputInfo
import org.ton.TvmParameterInfo.CellInfo
import org.ton.TvmParameterInfo.DataCellInfo
import org.ton.TvmParameterInfo.SliceInfo
import org.ton.TvmParameterInfo.UnknownCellInfo
import org.ton.bytecode.MethodId
import org.ton.bytecode.TsaArtificialExecuteContInst
import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.TvmAppActionsInst
import org.ton.bytecode.TvmAppAddrInst
import org.ton.bytecode.TvmAppConfigInst
import org.ton.bytecode.TvmAppCryptoInst
import org.ton.bytecode.TvmAppCurrencyInst
import org.ton.bytecode.TvmAppGasInst
import org.ton.bytecode.TvmAppGlobalInst
import org.ton.bytecode.TvmArithmBasicAddInst
import org.ton.bytecode.TvmArithmBasicAddconstInst
import org.ton.bytecode.TvmArithmBasicDecInst
import org.ton.bytecode.TvmArithmBasicIncInst
import org.ton.bytecode.TvmArithmBasicInst
import org.ton.bytecode.TvmArithmBasicMulInst
import org.ton.bytecode.TvmArithmBasicMulconstInst
import org.ton.bytecode.TvmArithmBasicNegateInst
import org.ton.bytecode.TvmArithmBasicSubInst
import org.ton.bytecode.TvmArithmBasicSubrInst
import org.ton.bytecode.TvmArithmDivInst
import org.ton.bytecode.TvmArithmLogicalAbsInst
import org.ton.bytecode.TvmArithmLogicalAndInst
import org.ton.bytecode.TvmArithmLogicalBitsizeInst
import org.ton.bytecode.TvmArithmLogicalFitsInst
import org.ton.bytecode.TvmArithmLogicalFitsxInst
import org.ton.bytecode.TvmArithmLogicalInst
import org.ton.bytecode.TvmArithmLogicalLshiftInst
import org.ton.bytecode.TvmArithmLogicalLshiftVarInst
import org.ton.bytecode.TvmArithmLogicalMaxInst
import org.ton.bytecode.TvmArithmLogicalMinInst
import org.ton.bytecode.TvmArithmLogicalMinmaxInst
import org.ton.bytecode.TvmArithmLogicalNotInst
import org.ton.bytecode.TvmArithmLogicalOrInst
import org.ton.bytecode.TvmArithmLogicalPow2Inst
import org.ton.bytecode.TvmArithmLogicalRshiftInst
import org.ton.bytecode.TvmArithmLogicalRshiftVarInst
import org.ton.bytecode.TvmArithmLogicalUbitsizeInst
import org.ton.bytecode.TvmArithmLogicalUfitsInst
import org.ton.bytecode.TvmArithmLogicalUfitsxInst
import org.ton.bytecode.TvmArithmLogicalXorInst
import org.ton.bytecode.TvmArtificialInst
import org.ton.bytecode.TvmCellBuildInst
import org.ton.bytecode.TvmCellParseInst
import org.ton.bytecode.TvmCellValue
import org.ton.bytecode.TvmCodeBlock
import org.ton.bytecode.TvmCodepageInst
import org.ton.bytecode.TvmCompareIntChknanInst
import org.ton.bytecode.TvmCompareIntCmpInst
import org.ton.bytecode.TvmCompareIntEqintInst
import org.ton.bytecode.TvmCompareIntEqualInst
import org.ton.bytecode.TvmCompareIntGeqInst
import org.ton.bytecode.TvmCompareIntGreaterInst
import org.ton.bytecode.TvmCompareIntGtintInst
import org.ton.bytecode.TvmCompareIntInst
import org.ton.bytecode.TvmCompareIntIsnanInst
import org.ton.bytecode.TvmCompareIntLeqInst
import org.ton.bytecode.TvmCompareIntLessInst
import org.ton.bytecode.TvmCompareIntLessintInst
import org.ton.bytecode.TvmCompareIntNeqInst
import org.ton.bytecode.TvmCompareIntNeqintInst
import org.ton.bytecode.TvmCompareIntSgnInst
import org.ton.bytecode.TvmCompareOtherInst
import org.ton.bytecode.TvmCompareOtherSdcnttrail0Inst
import org.ton.bytecode.TvmCompareOtherSdemptyInst
import org.ton.bytecode.TvmCompareOtherSdeqInst
import org.ton.bytecode.TvmCompareOtherSemptyInst
import org.ton.bytecode.TvmCompareOtherSremptyInst
import org.ton.bytecode.TvmConstDataInst
import org.ton.bytecode.TvmConstDataPushcontInst
import org.ton.bytecode.TvmConstDataPushcontShortInst
import org.ton.bytecode.TvmConstDataPushrefInst
import org.ton.bytecode.TvmConstDataPushrefcontInst
import org.ton.bytecode.TvmConstDataPushrefsliceInst
import org.ton.bytecode.TvmConstDataPushsliceInst
import org.ton.bytecode.TvmConstDataPushsliceLongInst
import org.ton.bytecode.TvmConstIntInst
import org.ton.bytecode.TvmConstIntPushint16Inst
import org.ton.bytecode.TvmConstIntPushint4Inst
import org.ton.bytecode.TvmConstIntPushint8Inst
import org.ton.bytecode.TvmConstIntPushintLongInst
import org.ton.bytecode.TvmConstIntPushnanInst
import org.ton.bytecode.TvmConstIntPushnegpow2Inst
import org.ton.bytecode.TvmConstIntPushpow2Inst
import org.ton.bytecode.TvmConstIntPushpow2decInst
import org.ton.bytecode.TvmContBasicCallrefInst
import org.ton.bytecode.TvmContBasicCallxargsInst
import org.ton.bytecode.TvmContBasicCallxargsVarInst
import org.ton.bytecode.TvmContBasicCallxvarargsInst
import org.ton.bytecode.TvmContBasicExecuteInst
import org.ton.bytecode.TvmContBasicInst
import org.ton.bytecode.TvmContBasicJmpxInst
import org.ton.bytecode.TvmContBasicRetInst
import org.ton.bytecode.TvmContBasicRetaltInst
import org.ton.bytecode.TvmContConditionalCondselInst
import org.ton.bytecode.TvmContConditionalIfInst
import org.ton.bytecode.TvmContConditionalIfelseInst
import org.ton.bytecode.TvmContConditionalIfelserefInst
import org.ton.bytecode.TvmContConditionalIfjmpInst
import org.ton.bytecode.TvmContConditionalIfjmprefInst
import org.ton.bytecode.TvmContConditionalIfnotInst
import org.ton.bytecode.TvmContConditionalIfnotjmpInst
import org.ton.bytecode.TvmContConditionalIfnotjmprefInst
import org.ton.bytecode.TvmContConditionalIfnotrefInst
import org.ton.bytecode.TvmContConditionalIfnotretInst
import org.ton.bytecode.TvmContConditionalIfrefInst
import org.ton.bytecode.TvmContConditionalIfrefelseInst
import org.ton.bytecode.TvmContConditionalIfrefelserefInst
import org.ton.bytecode.TvmContConditionalIfretInst
import org.ton.bytecode.TvmContConditionalInst
import org.ton.bytecode.TvmContDictCalldictInst
import org.ton.bytecode.TvmContDictCalldictLongInst
import org.ton.bytecode.TvmContDictInst
import org.ton.bytecode.TvmContDictPreparedictInst
import org.ton.bytecode.TvmContLoopsInst
import org.ton.bytecode.TvmContRegistersComposInst
import org.ton.bytecode.TvmContRegistersComposaltInst
import org.ton.bytecode.TvmContRegistersComposbothInst
import org.ton.bytecode.TvmContRegistersInst
import org.ton.bytecode.TvmContRegistersPopctrInst
import org.ton.bytecode.TvmContRegistersPushctrInst
import org.ton.bytecode.TvmContRegistersSamealtInst
import org.ton.bytecode.TvmContRegistersSamealtsaveInst
import org.ton.bytecode.TvmContRegistersSaveInst
import org.ton.bytecode.TvmContRegistersSetcontctrInst
import org.ton.bytecode.TvmContStackInst
import org.ton.bytecode.TvmContinuation
import org.ton.bytecode.TvmDebugInst
import org.ton.bytecode.TvmDictInst
import org.ton.bytecode.TvmDictSpecialDictigetjmpzInst
import org.ton.bytecode.TvmDictSpecialDictpushconstInst
import org.ton.bytecode.TvmDictSpecialInst
import org.ton.bytecode.TvmExceptionsInst
import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmInstList
import org.ton.bytecode.TvmLambda
import org.ton.bytecode.TvmMainMethodLocation
import org.ton.bytecode.TvmMethod
import org.ton.bytecode.TvmOrdContinuation
import org.ton.bytecode.TvmRegisterSavelist
import org.ton.bytecode.TvmStackBasicInst
import org.ton.bytecode.TvmStackBasicNopInst
import org.ton.bytecode.TvmStackBasicPopInst
import org.ton.bytecode.TvmStackBasicPushInst
import org.ton.bytecode.TvmStackBasicXchg0iInst
import org.ton.bytecode.TvmStackBasicXchg0iLongInst
import org.ton.bytecode.TvmStackBasicXchg1iInst
import org.ton.bytecode.TvmStackBasicXchgIjInst
import org.ton.bytecode.TvmStackComplexBlkdrop2Inst
import org.ton.bytecode.TvmStackComplexBlkdropInst
import org.ton.bytecode.TvmStackComplexBlkpushInst
import org.ton.bytecode.TvmStackComplexBlkswapInst
import org.ton.bytecode.TvmStackComplexBlkswxInst
import org.ton.bytecode.TvmStackComplexChkdepthInst
import org.ton.bytecode.TvmStackComplexDepthInst
import org.ton.bytecode.TvmStackComplexDrop2Inst
import org.ton.bytecode.TvmStackComplexDropxInst
import org.ton.bytecode.TvmStackComplexDup2Inst
import org.ton.bytecode.TvmStackComplexInst
import org.ton.bytecode.TvmStackComplexMinusrollxInst
import org.ton.bytecode.TvmStackComplexOnlytopxInst
import org.ton.bytecode.TvmStackComplexOnlyxInst
import org.ton.bytecode.TvmStackComplexOver2Inst
import org.ton.bytecode.TvmStackComplexPickInst
import org.ton.bytecode.TvmStackComplexPopLongInst
import org.ton.bytecode.TvmStackComplexPu2xcInst
import org.ton.bytecode.TvmStackComplexPush2Inst
import org.ton.bytecode.TvmStackComplexPush3Inst
import org.ton.bytecode.TvmStackComplexPushLongInst
import org.ton.bytecode.TvmStackComplexPuxc2Inst
import org.ton.bytecode.TvmStackComplexPuxcInst
import org.ton.bytecode.TvmStackComplexPuxcpuInst
import org.ton.bytecode.TvmStackComplexReverseInst
import org.ton.bytecode.TvmStackComplexRevxInst
import org.ton.bytecode.TvmStackComplexRollxInst
import org.ton.bytecode.TvmStackComplexRotInst
import org.ton.bytecode.TvmStackComplexRotrevInst
import org.ton.bytecode.TvmStackComplexSwap2Inst
import org.ton.bytecode.TvmStackComplexTuckInst
import org.ton.bytecode.TvmStackComplexXc2puInst
import org.ton.bytecode.TvmStackComplexXchg2Inst
import org.ton.bytecode.TvmStackComplexXchg3AltInst
import org.ton.bytecode.TvmStackComplexXchg3Inst
import org.ton.bytecode.TvmStackComplexXchgxInst
import org.ton.bytecode.TvmStackComplexXcpu2Inst
import org.ton.bytecode.TvmStackComplexXcpuInst
import org.ton.bytecode.TvmStackComplexXcpuxcInst
import org.ton.bytecode.TvmTupleInst
import org.ton.bytecode.disassembleCell
import org.ton.targets.TvmTarget
import org.usvm.StepResult
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UInterpreter
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.TvmCellDataFieldManager
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmConcreteGeneralData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.RECEIVE_EXTERNAL_ID
import org.usvm.machine.TvmContext.Companion.RECEIVE_INTERNAL_ID
import org.usvm.machine.TvmContext.Companion.cellDataLengthField
import org.usvm.machine.TvmContext.Companion.cellRefsLengthField
import org.usvm.machine.TvmContext.Companion.sliceCellField
import org.usvm.machine.TvmContext.Companion.sliceDataPosField
import org.usvm.machine.TvmContext.Companion.sliceRefPosField
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.bigIntValue
import org.usvm.machine.extractMethodIdOrNull
import org.usvm.machine.intValue
import org.usvm.machine.state.C0Register
import org.usvm.machine.state.C1Register
import org.usvm.machine.state.C2Register
import org.usvm.machine.state.C3Register
import org.usvm.machine.state.C4Register
import org.usvm.machine.state.C5Register
import org.usvm.machine.state.C7Register
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmInitialStateData
import org.usvm.machine.state.TvmPathConstraints
import org.usvm.machine.state.TvmRefEmptyValue
import org.usvm.machine.state.TvmStack.TvmConcreteStackEntry
import org.usvm.machine.state.TvmStack.TvmStackCellValue
import org.usvm.machine.state.TvmStack.TvmStackSliceValue
import org.usvm.machine.state.TvmStack.TvmStackTupleValueConcreteNew
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.addContinuation
import org.usvm.machine.state.addInt
import org.usvm.machine.state.addOnStack
import org.usvm.machine.state.addTuple
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.allocateCell
import org.usvm.machine.state.bvMaxValueSignedExtended
import org.usvm.machine.state.bvMaxValueUnsignedExtended
import org.usvm.machine.state.bvMinValueSignedExtended
import org.usvm.machine.state.callContinuation
import org.usvm.machine.state.callContinuationComplex
import org.usvm.machine.state.callMethod
import org.usvm.machine.state.checkOutOfRange
import org.usvm.machine.state.checkOverflow
import org.usvm.machine.state.checkUnderflow
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.consumeGas
import org.usvm.machine.state.defineC0
import org.usvm.machine.state.defineC1
import org.usvm.machine.state.defineC2
import org.usvm.machine.state.defineC3
import org.usvm.machine.state.defineC4
import org.usvm.machine.state.defineC5
import org.usvm.machine.state.defineC7
import org.usvm.machine.state.doBlkSwap
import org.usvm.machine.state.doPop
import org.usvm.machine.state.doPush
import org.usvm.machine.state.doPuxc
import org.usvm.machine.state.doSwap
import org.usvm.machine.state.doWithCtx
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.doXchg
import org.usvm.machine.state.doXchg2
import org.usvm.machine.state.doXchg3
import org.usvm.machine.state.generateSymbolicCell
import org.usvm.machine.state.getBalance
import org.usvm.machine.state.getSliceRemainingBitsCount
import org.usvm.machine.state.getSliceRemainingRefsCount
import org.usvm.machine.state.initContractInfo
import org.usvm.machine.state.initializeContractExecutionMemory
import org.usvm.machine.state.input.ReceiverInput
import org.usvm.machine.state.input.RecvExternalInput
import org.usvm.machine.state.input.RecvInternalInput
import org.usvm.machine.state.input.TvmStackInput
import org.usvm.machine.state.jumpToContinuation
import org.usvm.machine.state.killCurrentState
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.returnAltFromContinuation
import org.usvm.machine.state.returnFromContinuation
import org.usvm.machine.state.signedIntegerFitsBits
import org.usvm.machine.state.slicesAreEqual
import org.usvm.machine.state.switchToFirstMethodInContract
import org.usvm.machine.state.takeLastCell
import org.usvm.machine.state.takeLastContinuation
import org.usvm.machine.state.takeLastIntOrThrowTypeError
import org.usvm.machine.state.takeLastSlice
import org.usvm.machine.state.takeLastTuple
import org.usvm.machine.state.unsignedIntegerFitsBits
import org.usvm.machine.toMethodId
import org.usvm.machine.toTvmCell
import org.usvm.machine.tryCatchIf
import org.usvm.machine.types.TvmBuilderType
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmDataCellInfoStorage
import org.usvm.machine.types.TvmIntegerType
import org.usvm.machine.types.TvmSliceType
import org.usvm.machine.types.TvmType
import org.usvm.machine.types.TvmTypeSystem
import org.usvm.memory.UMemory
import org.usvm.memory.UWritableMemory
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGeExpr
import org.usvm.sizeSort
import org.usvm.solver.USatResult
import org.usvm.targets.UTargetsSet
import java.math.BigInteger

// TODO there are a lot of `scope.calcOnState` and `scope.doWithState` invocations that are not inline - optimize it
class TvmInterpreter(
    private val ctx: TvmContext,
    private val contractsCode: List<TsaContractCode>,
    val typeSystem: TvmTypeSystem,
    private val inputInfo: TvmInputInfo,
    var forkBlackList: UForkBlackList<TvmState, TvmInst> = UForkBlackList.createDefault(),
) : UInterpreter<TvmState>() {
    companion object {
        val logger = object : KLogging() {}.logger
    }

    private val exceptionsInterpreter = TvmExceptionsInterpreter(ctx)
    private val tupleInterpreter = TvmTupleInterpreter(ctx)
    private val dictOperationInterpreter = TvmDictOperationInterpreter(ctx)
    private val loopsInterpreter = TvmLoopsInterpreter(ctx)
    private val arithDivInterpreter = TvmArithDivInterpreter(ctx)
    private val cellInterpreter = TvmCellInterpreter(ctx)
    private val msgAddrInterpreter = TvmMessageAddrInterpreter(ctx)
    private val currencyInterpreter = TvmCurrencyInterpreter(ctx)
    private val configInterpreter = TvmConfigInterpreter(ctx)
    private val actionsInterpreter = TvmActionsInterpreter(ctx)
    private val cryptoInterpreter = TvmCryptoInterpreter(ctx)
    private val gasInterpreter = TvmGasInterpreter(ctx)
    private val globalsInterpreter = TvmGlobalsInterpreter(ctx)
    private val contStackInterpreter = TvmContinuationStackInterpreter(ctx)
    private val transactionInterpreter = TvmTransactionInterpreter(ctx)
    private val tsaCheckerFunctionsInterpreter = TsaCheckerFunctionsInterpreter(contractsCode)
    private val artificialInstInterpreter = TvmArtificialInstInterpreter(ctx, contractsCode, transactionInterpreter, tsaCheckerFunctionsInterpreter)
    private val postProcessor = TvmPostProcessor(ctx)

    fun getInitialState(
        startContractId: ContractId,
        concreteGeneralData: TvmConcreteGeneralData,
        concreteContractData: List<TvmConcreteContractData>,
        methodId: MethodId,
        targets: List<TvmTarget> = emptyList()
    ): TvmState {
        require(concreteContractData.size == contractsCode.size) {
            "concreteContractData must be given for all analyzed contracts"
        }

        val contractCode = contractsCode.getOrNull(startContractId)
            ?: error("Contract $startContractId not found.")

        val initOwnership = MutabilityOwnership()
        val pathConstraints = TvmPathConstraints(ctx, initOwnership)
        val memory = UMemory<TvmType, TvmCodeBlock>(ctx, initOwnership, pathConstraints.typeConstraints)
        val cellDataFieldManager = TvmCellDataFieldManager(ctx)
        val refEmptyValue = memory.initializeEmptyRefValues(cellDataFieldManager)

        val state = TvmState(
            ctx = ctx,
            ownership = initOwnership,
            entrypoint = contractCode.mainMethod,
            memory = memory,
            pathConstraints = pathConstraints,
            emptyRefValue = refEmptyValue,
            gasUsage = persistentListOf(),
            targets = UTargetsSet.from(targets),
            typeSystem = typeSystem,
            currentContract = startContractId,
            cellDataFieldManager = cellDataFieldManager,
            intercontractPath = persistentListOf(startContractId),
            analysisOfGetMethod = methodId != RECEIVE_INTERNAL_ID && methodId != RECEIVE_EXTERNAL_ID,
        )

        state.contractIdToC4Register = contractsCode.indices.associateWith {
            // If no concrete contract data is provided for the contract, consider it as symbolic
            val givenData = concreteContractData.getOrNull(it)?.contractC4
            val ref = if (givenData != null) {
                state.allocateCell(givenData.toTvmCell())
            } else {
                state.generateSymbolicCell()
            }
            C4Register(TvmCellValue(ref))
        }.toPersistentMap()

        val useRecvInternalInput = methodId == RECEIVE_INTERNAL_ID && ctx.tvmOptions.useReceiverInputs
        val useRecvExternalInput = methodId == RECEIVE_EXTERNAL_ID && ctx.tvmOptions.useReceiverInputs
        if (useRecvInternalInput) {
            val input = RecvInternalInput(state, concreteGeneralData, startContractId)
            state.initialInput = input
            state.currentInput = input
        } else if (useRecvExternalInput) {
            val input = RecvExternalInput(state, concreteGeneralData, startContractId)
            state.initialInput = input
            check(concreteGeneralData.initialSenderBits == null) {
                "Cannot take into account concrete sender if when using RecvExternal input"
            }
        } else {
            state.initialInput = TvmStackInput
            check(concreteGeneralData.initialOpcode == null && concreteGeneralData.initialSenderBits == null) {
                "Cannot take into account concrete initialOpcode or sender if not using RecvInternal input"
            }
        }

        state.contractIdToFirstElementOfC7 = contractsCode.mapIndexed { index, code ->
            index to state.initContractInfo(code, concreteContractData[index])
        }.toMap().toPersistentMap()
        state.contractIdToInitialData = contractsCode.indices.associateWith {
            val c4 = state.contractIdToC4Register[it]
                ?: error("c4 for contract $it not found")
            val c7 = state.contractIdToFirstElementOfC7[it]
                ?: error("First element of c7 for contract $it not found")
            TvmInitialStateData(c4.value.value, c7)
        }

        prepareMemoryForInitialState(state, startContractId)

        state.callStack.push(contractCode.mainMethod, returnSite = null)
        state.switchToFirstMethodInContract(contractCode, methodId)

        state.stateInitialized = true
        return state
    }

    private fun prepareMemoryForInitialState(
        state: TvmState,
        startContractId: ContractId,
    ) {
        val msgValue = when (val input = state.initialInput) {
            is ReceiverInput -> input.msgValue
            is TvmStackInput -> null
        }

        val allowInputStackValues = ctx.tvmOptions.enableInputValues && (state.initialInput is TvmStackInput)
        val executionMemory = initializeContractExecutionMemory(
            contractsCode,
            state,
            startContractId,
            msgValue,
            allowInputStackValues,
        )

        state.stack = executionMemory.stack
        state.registersOfCurrentContract = executionMemory.registers

        when (val input = state.initialInput) {
            is ReceiverInput -> {
                val configBalance = state.getBalance()
                    ?: error("Unexpected incorrect config balance value")

                val newInputInfo = hashMapOf<UConcreteHeapRef, CellInfo>()

                // take input info only for the last parameter (msg_body)
                if ((inputInfo.parameterInfos.keys - setOf(0)).isNotEmpty()) {
                    error("Can take into account input info only for msg_body in case of recv_internal but got $inputInfo")
                }

                val lastInputInfo = inputInfo.parameterInfos[0]?.let {
                    (it as? SliceInfo)
                        ?: error("Incorrect input info for message body")
                }
                val msgBodyCellNonBounced = state.memory.readField(input.msgBodySliceNonBounced, sliceCellField, ctx.addressSort) as UConcreteHeapRef

                if (lastInputInfo != null) {
                    newInputInfo[msgBodyCellNonBounced] = lastInputInfo.cellInfo
                } else {
                    newInputInfo[msgBodyCellNonBounced] = UnknownCellInfo
                }

                val srcAddressCell = input.srcAddressCell
                if (srcAddressCell != null) {
                    newInputInfo[srcAddressCell] = DataCellInfo(TlbFullMsgAddrLabel)
                }

                val dataCellInfoStorage = TvmDataCellInfoStorage.build(
                    state = state,
                    info = TvmInputInfo(),
                    additionalCellLabels = newInputInfo,
                    additionalSliceToCell = mapOf(input.msgBodySliceNonBounced to msgBodyCellNonBounced),
                )
                setDataCellInfoStorageAndSetModel(state, dataCellInfoStorage)

                input.addressSlices.forEach {
                    dataCellInfoStorage.mapper.addAddressSlice(it)
                }

                // prepare stack
                executionMemory.stack.addInt(configBalance)
                executionMemory.stack.addInt(input.msgValue)
                executionMemory.stack.addStackEntry(TvmConcreteStackEntry(TvmStackCellValue(input.constructFullMessage(state))))
                executionMemory.stack.addStackEntry(TvmConcreteStackEntry(TvmStackSliceValue(input.msgBodySliceMaybeBounced)))

                // Save msgBody for inter-contract
                if (ctx.tvmOptions.intercontractOptions.isIntercontractEnabled) {
                    state.lastMsgBodySlice = input.msgBodySliceMaybeBounced
                }
            }

            is TvmStackInput -> {
                val dataCellInfoStorage = TvmDataCellInfoStorage.build(
                    state,
                    inputInfo,
                    additionalLabels = setOf(TlbFullMsgAddrLabel, TlbBasicMsgAddrLabel),
                )
                setDataCellInfoStorageAndSetModel(state, dataCellInfoStorage)
            }
        }
    }

    private fun setDataCellInfoStorageAndSetModel(state: TvmState, dataCellInfoStorage: TvmDataCellInfoStorage) {
        state.dataCellInfoStorage = dataCellInfoStorage
        state.cellDataFieldManager.addressToLabelMapper = dataCellInfoStorage.mapper

        val structuralConstraints = state.dataCellInfoStorage.mapper.getInitialStructuralConstraints(state)
        state.pathConstraints += structuralConstraints

        val solver = ctx.solver<TvmType>()

        val kModel = solver.check(state.pathConstraints) as? USatResult
            ?: error("Cannot construct model for initial state")
        val model = kModel.model
        state.models = listOf(model)
    }

    private fun UWritableMemory<TvmType>.initializeEmptyRefValues(
        cellDataFieldManager: TvmCellDataFieldManager
    ): TvmRefEmptyValue = with(ctx) {
        val emptyCell = allocStatic(TvmCellType)
        cellDataFieldManager.writeCellData(this@initializeEmptyRefValues, emptyCell, mkBv(0, cellDataSort))
        writeField(emptyCell, cellRefsLengthField, sizeSort, mkSizeExpr(0), guard = trueExpr)
        writeField(emptyCell, cellDataLengthField, sizeSort, mkSizeExpr(0), guard = trueExpr)

        val emptyBuilder = allocStatic(TvmBuilderType)
        cellDataFieldManager.writeCellData(this@initializeEmptyRefValues, emptyBuilder, mkBv(0, cellDataSort))
        writeField(emptyBuilder, cellRefsLengthField, sizeSort, mkSizeExpr(0), guard = trueExpr)
        writeField(emptyBuilder, cellDataLengthField, sizeSort, mkSizeExpr(0), guard = trueExpr)

        val emptySlice = allocStatic(TvmSliceType)
        writeField(emptySlice, sliceCellField, addressSort, emptyCell, guard = trueExpr)
        writeField(emptySlice, sliceRefPosField, sizeSort, mkSizeExpr(0), guard = trueExpr)
        writeField(emptySlice, sliceDataPosField, sizeSort, mkSizeExpr(0), guard = trueExpr)

        TvmRefEmptyValue(emptyCell, emptySlice, emptyBuilder)
    }

    fun postProcessStates(states: Collection<TvmState>): List<TvmState> {
        return states.filter { state ->
            val scope = TvmStepScopeManager(state, UForkBlackList.createDefault(), allowFailuresOnCurrentStep = true)

            postProcessor.postProcessState(scope)
                ?: return@filter false

            val globalStructuralConstraintsHolder = state.globalStructuralConstraintsHolder
            globalStructuralConstraintsHolder.applyTo(scope) != null
        }
    }

    override fun step(state: TvmState): StepResult<TvmState> {
        val stmt = state.lastStmt

        logger.debug("Step: {}", stmt)

        val initialGasUsage = state.gasUsage
        val globalStructuralConstraintsHolder = state.globalStructuralConstraintsHolder

        val allowFailures = state.allowFailures && !ctx.tvmOptions.excludeExecutionsWithFailures
        var scope = TvmStepScopeManager(state, forkBlackList, allowFailures)

        tryCatchIf(
            condition = ctx.tvmOptions.quietMode,
            body = {
                visit(scope, stmt)

                globalStructuralConstraintsHolder.applyTo(scope)
                    ?: error("Could not apply structural constraints")  // TODO: add special exit for that
            },
            exceptionHandler = {
                logger.warn(it) {
                    "Exception is thrown during the interpretation of $stmt instruction, dropping the state."
                }

                // clear forked states, as they can be corrupted
                scope = TvmStepScopeManager(state, forkBlackList, allowFailures)
                scope.killCurrentState()
            }
        )

        return scope.stepResult().apply {
            if (originalStateAlive && state.gasUsage === initialGasUsage || forkedStates.any { it.gasUsage === initialGasUsage }) {
                TODO("Gas usage was not updated after: $stmt")
            }
        }
    }

    private fun visit(scope: TvmStepScopeManager, stmt: TvmInst) {
        when (stmt) {
            is TvmArtificialInst -> artificialInstInterpreter.visit(scope, stmt)
            is TvmStackBasicInst -> visitBasicStackInst(scope, stmt)
            is TvmStackComplexInst -> visitComplexStackInst(scope, stmt)
            is TvmConstIntInst -> visitConstantIntInst(scope, stmt)
            is TvmConstDataInst -> visitConstantDataInst(scope, stmt)
            is TvmArithmBasicInst -> visitArithmeticInst(scope, stmt)
            is TvmArithmDivInst -> arithDivInterpreter.visitArithmeticDivInst(scope, stmt)
            is TvmArithmLogicalInst -> visitArithmeticLogicalInst(scope, stmt)
            is TvmCompareIntInst -> visitComparisonIntInst(scope, stmt)
            is TvmCompareOtherInst -> visitComparisonOtherInst(scope, stmt)
            is TvmCellBuildInst -> cellInterpreter.visitCellBuildInst(scope, stmt)
            is TvmCellParseInst -> cellInterpreter.visitCellParseInst(scope, stmt)
            is TvmContBasicInst -> visitTvmBasicControlFlowInst(scope, stmt)
            is TvmContConditionalInst -> visitTvmConditionalControlFlowInst(scope, stmt)
            is TvmContRegistersInst -> visitTvmSaveControlFlowInst(scope, stmt)
            is TvmContDictInst -> visitTvmDictionaryJumpInst(scope, stmt)
            is TvmDebugInst -> visitDebugInst(scope, stmt)
            is TvmCodepageInst -> visitCodepageInst(scope, stmt)
            is TvmDictSpecialInst -> visitDictControlFlowInst(scope, stmt)
            is TvmExceptionsInst -> exceptionsInterpreter.visitExceptionInst(scope, stmt)
            is TvmTupleInst -> tupleInterpreter.visitTvmTupleInst(scope, stmt)
            is TvmDictInst -> dictOperationInterpreter.visitTvmDictInst(scope, stmt)
            is TvmContLoopsInst -> loopsInterpreter.visitTvmContLoopsInst(scope, stmt)
            is TvmAppAddrInst -> msgAddrInterpreter.visitAddrInst(scope, stmt)
            is TvmAppCurrencyInst -> currencyInterpreter.visitCurrencyInst(scope, stmt)
            is TvmAppConfigInst -> configInterpreter.visitConfigInst(scope, stmt)
            is TvmAppActionsInst -> actionsInterpreter.visitActionsStmt(scope, stmt)
            is TvmAppCryptoInst -> cryptoInterpreter.visitCryptoStmt(scope, stmt)
            is TvmAppGasInst -> gasInterpreter.visitGasInst(scope, stmt)
            is TvmAppGlobalInst -> globalsInterpreter.visitGlobalInst(scope, stmt)
            is TvmContStackInst -> contStackInterpreter.visitContStackInst(scope, stmt)
            else -> TODO("$stmt")
        }
    }

    private fun visitBasicStackInst(scope: TvmStepScopeManager, stmt: TvmStackBasicInst) {
        scope.consumeDefaultGas(stmt)

        when (stmt) {
            is TvmStackBasicNopInst -> {
                // Do nothing
            }
            is TvmStackBasicPopInst -> doPop(scope, stmt.i)
            is TvmStackBasicPushInst -> doPush(scope, stmt.i)
            is TvmStackBasicXchg0iInst -> doXchg(scope, stmt.i, 0)
            is TvmStackBasicXchgIjInst -> doXchg(scope, stmt.i, stmt.j)
            is TvmStackBasicXchg1iInst -> doXchg(scope, stmt.i, 1)
            is TvmStackBasicXchg0iLongInst -> doXchg(scope, stmt.i, 0)
        }

        scope.doWithState {
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitComplexStackInst(
        scope: TvmStepScopeManager,
        stmt: TvmStackComplexInst
    ) {
        scope.consumeDefaultGas(stmt)

        when (stmt) {
            is TvmStackComplexBlkdrop2Inst -> scope.doWithState {
                stack.blkDrop2(stmt.i, stmt.j)
            }
            is TvmStackComplexReverseInst -> scope.doWithState {
                stack.reverse(stmt.i + 2, stmt.j)
            }
            is TvmStackComplexBlkswapInst -> scope.doWithState {
                stack.doBlkSwap(stmt.i, stmt.j)
            }
            is TvmStackComplexRotInst -> scope.doWithState {
                stack.doBlkSwap(0, 1)
            }
            is TvmStackComplexBlkdropInst -> scope.doWithState {
                stack.blkDrop2(stmt.i, 0)
            }
            is TvmStackComplexBlkpushInst -> scope.doWithState {
                repeat(stmt.i) {
                    stack.push(stmt.j)
                }
            }
            is TvmStackComplexBlkswxInst -> scope.doWithState {
                val j = takeLastIntOrThrowTypeError() ?: return@doWithState
                val i = takeLastIntOrThrowTypeError() ?: return@doWithState
                val concreteI = i.extractConcrete(stmt)
                val concreteJ = j.extractConcrete(stmt)
                stack.doBlkSwap(concreteI - 1, concreteJ - 1)
            }

            is TvmStackComplexDrop2Inst -> scope.doWithState {
                stack.pop(0)
                stack.pop(0)
            }
            is TvmStackComplexDropxInst -> scope.doWithState {
                val i = takeLastIntOrThrowTypeError() ?: return@doWithState
                val concreteI = i.extractConcrete(stmt)
                stack.blkDrop2(concreteI, 0)
            }
            is TvmStackComplexDup2Inst -> scope.doWithState {
                stack.push(1)
                stack.push(1)
            }
            is TvmStackComplexPopLongInst -> doPop(scope, stmt.i)
            is TvmStackComplexPush2Inst -> scope.doWithState {
                stack.push(stmt.i)
                stack.push(stmt.j + 1)
            }
            is TvmStackComplexPush3Inst -> scope.doWithState {
                stack.push(stmt.i)
                stack.push(stmt.j + 1)
                stack.push(stmt.k + 2)
            }
            is TvmStackComplexPushLongInst -> doPush(scope, stmt.i)
            is TvmStackComplexXchg2Inst -> scope.doWithState {
                stack.doXchg2(stmt.i, stmt.j)
            }
            is TvmStackComplexOver2Inst -> scope.doWithState {
                stack.push(3)
                stack.push(3)
            }
            is TvmStackComplexSwap2Inst -> scope.doWithState {
                stack.doBlkSwap(1, 1)
            }
            is TvmStackComplexXcpuInst -> scope.doWithState {
                stack.swap(stmt.i, 0)
                stack.push(stmt.j)
            }
            is TvmStackComplexTuckInst -> scope.doWithState {
                stack.swap(0, 1)
                stack.push(1)
            }
            is TvmStackComplexMinusrollxInst -> scope.doWithState {
                val i = takeLastIntOrThrowTypeError() ?: return@doWithState
                val concreteI = i.extractConcrete(stmt)
                stack.doBlkSwap(concreteI - 1, 0)
            }
            is TvmStackComplexRollxInst -> scope.doWithState {
                val i = takeLastIntOrThrowTypeError() ?: return@doWithState
                val concreteI = i.extractConcrete(stmt)
                stack.doBlkSwap(0, concreteI - 1)
            }
            is TvmStackComplexPickInst -> scope.doWithState {
                val i = takeLastIntOrThrowTypeError() ?: return@doWithState
                val concreteI = i.extractConcrete(stmt)
                stack.push(concreteI)
            }
            is TvmStackComplexPuxcInst -> scope.doWithState {
                stack.doPuxc(stmt.i, stmt.j - 1)
            }
            is TvmStackComplexRevxInst -> scope.doWithState {
                val j = takeLastIntOrThrowTypeError() ?: return@doWithState
                val i = takeLastIntOrThrowTypeError() ?: return@doWithState
                val concreteI = i.extractConcrete(stmt)
                val concreteJ = j.extractConcrete(stmt)
                stack.reverse(concreteI, concreteJ)
            }
            is TvmStackComplexRotrevInst -> scope.doWithState {
                stack.swap(1, 2)
                stack.swap(0, 2)
            }
            is TvmStackComplexXchgxInst -> scope.doWithState {
                val i = takeLastIntOrThrowTypeError() ?: return@doWithState
                val concreteI = i.extractConcrete(stmt)
                stack.swap(0, concreteI)
            }
            is TvmStackComplexPu2xcInst -> scope.doWithState {
                stack.push(stmt.i)
                stack.swap(0, 1)
                stack.doPuxc(stmt.j, stmt.k - 1)
            }
            is TvmStackComplexPuxc2Inst -> scope.doWithState {
                stack.push(stmt.i)
                stack.swap(0, 2)
                stack.doXchg2(stmt.j, stmt.k)
            }
            is TvmStackComplexPuxcpuInst -> scope.doWithState {
                stack.doPuxc(stmt.i, stmt.j - 1)
                stack.push(stmt.k)
            }
            is TvmStackComplexXc2puInst -> scope.doWithState {
                stack.doXchg2(stmt.i, stmt.j)
                stack.push(stmt.k)
            }
            is TvmStackComplexXchg3Inst -> scope.doWithState {
                stack.doXchg3(stmt.i, stmt.j, stmt.k)
            }
            is TvmStackComplexXchg3AltInst -> scope.doWithState {
                stack.doXchg3(stmt.i, stmt.j, stmt.k)
            }
            is TvmStackComplexXcpu2Inst -> scope.doWithState {
                stack.swap(stmt.i, 0)
                stack.push(stmt.j)
                stack.push(stmt.k + 1)
            }
            is TvmStackComplexXcpuxcInst -> scope.doWithState {
                stack.swap(1, stmt.i)
                stack.doPuxc(stmt.j, stmt.k - 1)
            }

            is TvmStackComplexDepthInst -> TODO("Cannot implement stack depth yet (TvmStackComplexDepthInst)")
            is TvmStackComplexChkdepthInst -> TODO("Cannot implement stack depth yet (TvmStackComplexChkdepthInst)")
            is TvmStackComplexOnlytopxInst -> TODO("??")
            is TvmStackComplexOnlyxInst -> TODO("??")
        }

        scope.doWithState {
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitConstantIntInst(scope: TvmStepScopeManager, stmt: TvmConstIntInst) {
        scope.consumeDefaultGas(stmt)
        scope.doWithState {
            val value = stmt.bv257value(ctx)
            stack.addInt(value)
            newStmt(stmt.nextStmt())
        }
    }

    private fun TvmConstIntInst.bv257value(ctx: TvmContext): UExpr<TvmInt257Sort> = with(ctx) {
        when (this@bv257value) {
            is TvmConstIntPushint4Inst -> {
                check(i in 0..15) { "Unexpected $i" }
                val x = if (i > 10) i - 16 else i // Normalize wrt docs
                x.toBv257()
            }
            is TvmConstIntPushint8Inst -> x.toBv257()
            is TvmConstIntPushint16Inst -> x.toBv257()
            is TvmConstIntPushintLongInst -> BigInteger(x).toBv257()
            is TvmConstIntPushnanInst -> TODO("NaN value")
            is TvmConstIntPushpow2Inst -> {
                check(x in 0..255) { "Unexpected power $x" }

                if (x == 255) {
                    TODO("NaN value")
                }

                BigInteger.valueOf(2).pow(x + 1).toBv257()
            }
            is TvmConstIntPushnegpow2Inst -> {
                check(x in 0..255) { "Unexpected power $x" }
                // todo: nothing in docs about nan
                BigInteger.valueOf(-2).pow(x + 1).toBv257()
            }
            is TvmConstIntPushpow2decInst -> {
                check(x in 0..255) { "Unexpected power $x" }
                // todo: nothing in docs about nan
                (BigInteger.valueOf(2).pow(x + 1) - BigInteger.ONE).toBv257()
            }
        }
    }

    private fun visitConstantDataInst(scope: TvmStepScopeManager, stmt: TvmConstDataInst) {
        when (stmt) {
            is TvmConstDataPushcontShortInst -> {
                visitPushContShortInst(scope, stmt)
            }
            is TvmConstDataPushrefInst -> {
                val allocatedCell = scope.calcOnState { allocateCell(stmt.c) }

                scope.doWithState {
                    addOnStack(allocatedCell, TvmCellType)
                    newStmt(stmt.nextStmt())
                }
                scope.consumeDefaultGas(stmt)
            }
            is TvmConstDataPushrefsliceInst -> {
                val allocatedCell = scope.calcOnState { allocateCell(stmt.c) }
                val allocatedSlice = scope.calcOnState { allocSliceFromCell(allocatedCell) }

                scope.doWithState {
                    consumeGas(118)  // TODO: complex gas 118/43
                    addOnStack(allocatedSlice, TvmSliceType)
                    newStmt(stmt.nextStmt())
                }
            }
            is TvmConstDataPushsliceInst -> {
                check(stmt.s.refs.isEmpty()) { "Unexpected refs in $stmt" }

                scope.doWithStateCtx {
                    val slice = scope.calcOnState { allocSliceFromCell(stmt.s) }

                    scope.addOnStack(slice, TvmSliceType)
                    newStmt(stmt.nextStmt())
                }
                scope.consumeDefaultGas(stmt)
            }
            is TvmConstDataPushcontInst -> {
                scope.doWithStateCtx {
                    val continuationValue = TvmOrdContinuation(TvmLambda(stmt.c.list.toMutableList()), stmt.c.raw)
                    stack.addContinuation(continuationValue)

                    newStmt(stmt.nextStmt())
                }
                scope.consumeDefaultGas(stmt)
            }
            is TvmConstDataPushrefcontInst -> {
                scope.doWithStateCtx {
                    val continuationValue = TvmOrdContinuation(TvmLambda(stmt.c.list.toMutableList()), stmt.c.raw)
                    stack.addContinuation(continuationValue)

                    newStmt(stmt.nextStmt())
                }
                scope.doWithState {
                    // TODO: compex gas
                    consumeGas(118)
                }
            }
            is TvmConstDataPushsliceLongInst -> {
                if (stmt.slice.refs.isNotEmpty()) {
                    TODO("Non-empty refs in TvmConstDataPushsliceLongInst")
                }

                scope.doWithStateCtx {
                    val slice = scope.calcOnState { allocSliceFromCell(stmt.slice) }

                    scope.addOnStack(slice, TvmSliceType)
                    newStmt(stmt.nextStmt())
                }
                scope.consumeDefaultGas(stmt)
            }
            else -> TODO("$stmt")
        }
    }

    private fun visitPushContShortInst(scope: TvmStepScopeManager, stmt: TvmConstDataPushcontShortInst) {
        scope.doWithState {
            val lambda = TvmLambda(stmt.c.list.toMutableList(), stmt)
            val continuationValue = TvmOrdContinuation(lambda, stmt.c.raw)

            stack.addContinuation(continuationValue)
            newStmt(stmt.nextStmt())
        }
        scope.consumeDefaultGas(stmt)
    }

    private fun visitArithmeticInst(scope: TvmStepScopeManager, stmt: TvmArithmBasicInst) {
        scope.consumeDefaultGas(stmt)

        with(ctx) {
            val result = when (stmt) {
                is TvmArithmBasicAddInst -> {
                    val secondOperand = scope.takeLastIntOrThrowTypeError() ?: return
                    val firstOperand = scope.takeLastIntOrThrowTypeError() ?: return
                    // TODO optimize using ksmt implementation?
                    val resNoOverflow = mkBvAddNoOverflowExpr(firstOperand, secondOperand, isSigned = true)
                    checkOverflow(resNoOverflow, scope) ?: return
                    val resNoUnderflow = mkBvAddNoUnderflowExpr(firstOperand, secondOperand)
                    checkUnderflow(resNoUnderflow, scope) ?: return

                    mkBvAddExpr(firstOperand, secondOperand)
                }

                is TvmArithmBasicMulInst -> {
                    val secondOperand = scope.takeLastIntOrThrowTypeError() ?: return
                    val firstOperand = scope.takeLastIntOrThrowTypeError() ?: return
                    // TODO optimize using ksmt implementation?
                    val resNoOverflow = mkBvMulNoOverflowExpr(firstOperand, secondOperand, isSigned = true)
                    checkOverflow(resNoOverflow, scope) ?: return
                    val resNoUnderflow = mkBvMulNoUnderflowExpr(firstOperand, secondOperand)
                    checkUnderflow(resNoUnderflow, scope) ?: return

                    mkBvMulExpr(firstOperand, secondOperand)
                }
                is TvmArithmBasicAddconstInst -> {
                    val firstOperand = scope.takeLastIntOrThrowTypeError() ?: return
                    val secondOperand = stmt.c.toBv257()

                    // TODO optimize using ksmt implementation?
                    val resNoOverflow = mkBvAddNoOverflowExpr(firstOperand, secondOperand, isSigned = true)
                    checkOverflow(resNoOverflow, scope) ?: return
                    val resNoUnderflow = mkBvAddNoUnderflowExpr(firstOperand, secondOperand)
                    checkUnderflow(resNoUnderflow, scope) ?: return

                    mkBvAddExpr(firstOperand, secondOperand)
                }
                is TvmArithmBasicMulconstInst -> {
                    val firstOperand = scope.takeLastIntOrThrowTypeError() ?: return
                    val secondOperand = stmt.c.toBv257()

                    // TODO optimize using ksmt implementation?
                    val resNoOverflow = mkBvMulNoOverflowExpr(firstOperand, secondOperand, isSigned = true)
                    checkOverflow(resNoOverflow, scope) ?: return
                    val resNoUnderflow = mkBvMulNoUnderflowExpr(firstOperand, secondOperand)
                    checkUnderflow(resNoUnderflow, scope) ?: return

                    mkBvMulExpr(firstOperand, secondOperand)
                }

                is TvmArithmBasicIncInst -> {
                    val firstOperand = scope.takeLastIntOrThrowTypeError() ?: return
                    val secondOperand = oneValue

                    // TODO optimize using ksmt implementation?
                    val resNoOverflow = mkBvAddNoOverflowExpr(firstOperand, secondOperand, isSigned = true)
                    checkOverflow(resNoOverflow, scope) ?: return
                    val resNoUnderflow = mkBvAddNoUnderflowExpr(firstOperand, secondOperand)
                    checkUnderflow(resNoUnderflow, scope) ?: return

                    mkBvAddExpr(firstOperand, secondOperand)
                }
                is TvmArithmBasicDecInst -> {
                    val firstOperand = scope.takeLastIntOrThrowTypeError() ?: return
                    val secondOperand = oneValue

                    // TODO optimize using ksmt implementation?
                    val resNoOverflow = mkBvSubNoOverflowExpr(firstOperand, secondOperand)
                    checkOverflow(resNoOverflow, scope) ?: return
                    val resNoUnderflow = mkBvSubNoUnderflowExpr(firstOperand, secondOperand, isSigned = true)
                    checkUnderflow(resNoUnderflow, scope) ?: return

                    mkBvSubExpr(firstOperand, secondOperand)
                }
                is TvmArithmBasicNegateInst -> {
                    val operand = scope.takeLastIntOrThrowTypeError() ?: return

                    val noPossibleOverflow = operand neq min257BitValue
                    scope.fork(
                        noPossibleOverflow,
                        falseStateIsExceptional = true,
                        blockOnFalseState = throwIntegerOverflowError
                    ) ?: return

                    mkBvNegationExpr(operand)
                }
                is TvmArithmBasicSubInst -> {
                    doSubtraction(scope)
                        ?: return
                }
                is TvmArithmBasicSubrInst -> {
                    doSwap(scope)
                    doSubtraction(scope)
                        ?: return
                }
            }

            scope.doWithState {
                stack.addInt(result)
                newStmt(stmt.nextStmt())
            }
        }
    }

    private fun TvmContext.doSubtraction(
        scope: TvmStepScopeManager,
    ): UExpr<TvmInt257Sort>? {
        val secondOperand = scope.takeLastIntOrThrowTypeError()
            ?: return null
        val firstOperand = scope.takeLastIntOrThrowTypeError()
            ?: return null

        // TODO optimize using ksmt implementation?
        val resNoOverflow = mkBvSubNoOverflowExpr(firstOperand, secondOperand)
        checkOverflow(resNoOverflow, scope)
            ?: return null
        val resNoUnderflow = mkBvSubNoUnderflowExpr(firstOperand, secondOperand, isSigned = true)
        checkUnderflow(resNoUnderflow, scope)
            ?: return null

        return mkBvSubExpr(firstOperand, secondOperand)
    }

    private fun visitArithmeticLogicalInst(scope: TvmStepScopeManager, stmt: TvmArithmLogicalInst): Unit = with(ctx) {
        val result: UExpr<TvmInt257Sort> = when (stmt) {
            is TvmArithmLogicalOrInst -> {
                scope.consumeDefaultGas(stmt)

                val secondOperand = scope.takeLastIntOrThrowTypeError() ?: return
                val firstOperand = scope.takeLastIntOrThrowTypeError() ?: return
                mkBvOrExpr(firstOperand, secondOperand)
            }
            is TvmArithmLogicalXorInst -> {
                scope.consumeDefaultGas(stmt)

                val secondOperand = scope.takeLastIntOrThrowTypeError() ?: return
                val firstOperand = scope.takeLastIntOrThrowTypeError() ?: return
                mkBvXorExpr(firstOperand, secondOperand)
            }
            is TvmArithmLogicalAndInst -> {
                scope.consumeDefaultGas(stmt)

                val secondOperand = scope.takeLastIntOrThrowTypeError() ?: return
                val firstOperand = scope.takeLastIntOrThrowTypeError() ?: return
                mkBvAndExpr(firstOperand, secondOperand)
            }
            is TvmArithmLogicalNotInst -> {
                scope.doWithState { consumeGas(18) } // todo: 26 in docs, but 18 in concrete execution

                val value = scope.takeLastIntOrThrowTypeError() ?: return
                mkBvNotExpr(value)
            }
            is TvmArithmLogicalAbsInst -> {
                scope.consumeDefaultGas(stmt)

                val value = scope.takeLastIntOrThrowTypeError() ?: return
                checkOverflow(mkBvNegationNoOverflowExpr(value), scope) ?: return

                mkIte(
                    mkBvSignedLessExpr(value, zeroValue),
                    mkBvNegationExpr(value),
                    value
                )
            }
            is TvmArithmLogicalMaxInst -> {
                scope.consumeDefaultGas(stmt)

                val secondOperand = scope.takeLastIntOrThrowTypeError() ?: return
                val firstOperand = scope.takeLastIntOrThrowTypeError() ?: return

                mkIte(
                    condition = mkBvSignedGreaterOrEqualExpr(firstOperand, secondOperand),
                    trueBranch = firstOperand,
                    falseBranch = secondOperand
                )
            }
            is TvmArithmLogicalMinInst -> {
                scope.consumeDefaultGas(stmt)

                val secondOperand = scope.takeLastIntOrThrowTypeError() ?: return
                val firstOperand = scope.takeLastIntOrThrowTypeError() ?: return

                mkIte(
                    condition = mkBvSignedGreaterOrEqualExpr(firstOperand, secondOperand),
                    trueBranch = secondOperand,
                    falseBranch = firstOperand
                )
            }
            is TvmArithmLogicalMinmaxInst -> {
                scope.consumeDefaultGas(stmt)

                val secondOperand = scope.takeLastIntOrThrowTypeError() ?: return
                val firstOperand = scope.takeLastIntOrThrowTypeError() ?: return

                val min = mkIte(
                    condition = mkBvSignedGreaterOrEqualExpr(firstOperand, secondOperand),
                    trueBranch = secondOperand,
                    falseBranch = firstOperand
                )
                val max = mkIte(
                    condition = mkBvSignedGreaterOrEqualExpr(firstOperand, secondOperand),
                    trueBranch = firstOperand,
                    falseBranch = secondOperand
                )

                scope.doWithState {
                    stack.addInt(min)
                    stack.addInt(max)
                    newStmt(stmt.nextStmt())
                }

                return
            }
            is TvmArithmLogicalPow2Inst -> {
                scope.consumeDefaultGas(stmt)

                val exp = scope.takeLastIntOrThrowTypeError() ?: return
                val notOutOfRangeExpr = unsignedIntegerFitsBits(exp, 10u)
                checkOutOfRange(notOutOfRangeExpr, scope) ?: return

                val resNoOverflow = unsignedIntegerFitsBits(exp, 8u)
                checkOverflow(resNoOverflow, scope) ?: return

                mkBvShiftLeftExpr(oneValue, exp)
            }
            is TvmArithmLogicalLshiftInst -> {
                scope.consumeDefaultGas(stmt)

                val value = scope.takeLastIntOrThrowTypeError() ?: return
                val shift = stmt.c + 1
                val shiftValue = shift.toBv257()
                check(shift in 1..256) { "Unexpected shift $shift" }

                val maxArgValue = mkBvArithShiftRightExpr(bvMaxValueSigned(TvmContext.INT_BITS), shiftValue)
                val minArgValue = mkBvArithShiftRightExpr(bvMinValueSigned(TvmContext.INT_BITS), shiftValue)
                val resNoOverflow = mkAnd(
                    mkBvSignedLessOrEqualExpr(minArgValue, value),
                    mkBvSignedLessOrEqualExpr(value, maxArgValue)
                )
                checkOverflow(resNoOverflow, scope) ?: return

                mkBvShiftLeftExpr(value, shift.toBv257())
            }
            is TvmArithmLogicalLshiftVarInst -> {
                scope.consumeDefaultGas(stmt)

                val shift = scope.takeLastIntOrThrowTypeError() ?: return
                val value = scope.takeLastIntOrThrowTypeError() ?: return
                val notOutOfRangeExpr = unsignedIntegerFitsBits(shift, 10u)
                checkOutOfRange(notOutOfRangeExpr, scope) ?: return

                val maxArgValue = mkBvArithShiftRightExpr(bvMaxValueSigned(TvmContext.INT_BITS), shift)
                val minArgValue = mkBvArithShiftRightExpr(bvMinValueSigned(TvmContext.INT_BITS), shift)
                val resNoOverflow = mkAnd(
                    mkBvSignedLessOrEqualExpr(shift, 256.toBv257()),
                    mkBvSignedLessOrEqualExpr(minArgValue, value),
                    mkBvSignedLessOrEqualExpr(value, maxArgValue),
                )

                checkOverflow(resNoOverflow, scope) ?: return

                mkBvShiftLeftExpr(value, shift)
            }
            is TvmArithmLogicalRshiftInst -> {
                scope.doWithState { consumeGas(26) } // todo: 18 in docs, but 26 in concrete execution

                val value = scope.takeLastIntOrThrowTypeError() ?: return
                val shift = stmt.c + 1
                check(shift in 1..256) { "Unexpected shift $shift" }

                mkBvArithShiftRightExpr(value, shift.toBv257())
            }
            is TvmArithmLogicalRshiftVarInst -> {
                scope.consumeDefaultGas(stmt)

                val shift = scope.takeLastIntOrThrowTypeError() ?: return
                val value = scope.takeLastIntOrThrowTypeError() ?: return
                val notOutOfRangeExpr = unsignedIntegerFitsBits(shift, 10u)
                checkOutOfRange(notOutOfRangeExpr, scope) ?: return

                mkBvArithShiftRightExpr(value, shift)
            }
            is TvmArithmLogicalFitsInst -> {
                scope.doWithState { consumeGas(26) }

                val value = scope.takeLastIntOrThrowTypeError() ?: return
                val sizeBits = stmt.c + 1
                check(sizeBits in 1..256) { "Unexpected sizeBits $sizeBits" }

                val resNoOverflow = signedIntegerFitsBits(value, sizeBits.toUInt())
                checkOverflow(resNoOverflow, scope) ?: return

                value
            }
            is TvmArithmLogicalFitsxInst -> {
                scope.doWithState { consumeGas(26) }

                val sizeBits = scope.takeLastIntOrThrowTypeError() ?: return
                val value = scope.takeLastIntOrThrowTypeError() ?: return
                val notOutOfRangeExpr = unsignedIntegerFitsBits(sizeBits, 10u)
                checkOutOfRange(notOutOfRangeExpr, scope) ?: return

                val resNoOverflow = mkOr(
                    mkBvSignedGreaterOrEqualExpr(sizeBits, intBitsValue),
                    mkAnd(
                        mkBvSignedLessOrEqualExpr(bvMinValueSignedExtended(sizeBits), value),
                        mkBvSignedLessOrEqualExpr(value, bvMaxValueSignedExtended(sizeBits)),
                    ),
                )
                checkOverflow(resNoOverflow, scope) ?: return

                value
            }
            is TvmArithmLogicalUfitsInst -> {
                scope.doWithState { consumeGas(26) }

                val value = scope.takeLastIntOrThrowTypeError() ?: return
                val sizeBits = stmt.c + 1
                check(sizeBits in 1..256) { "Unexpected sizeBits $sizeBits" }

                val resNoOverflow = unsignedIntegerFitsBits(value, sizeBits.toUInt())
                checkOverflow(resNoOverflow, scope) ?: return

                value
            }
            is TvmArithmLogicalUfitsxInst -> {
                scope.doWithState { consumeGas(26) }

                val sizeBits = scope.takeLastIntOrThrowTypeError() ?: return
                val value = scope.takeLastIntOrThrowTypeError() ?: return
                val notOutOfRangeExpr = unsignedIntegerFitsBits(sizeBits, 10u)
                checkOutOfRange(notOutOfRangeExpr, scope) ?: return
                
                val sizeBitsUpperBound = mkBvSubExpr(intBitsValue, oneValue)

                val notNegativeValue = mkBvSignedGreaterOrEqualExpr(value, zeroValue)
                val resNoOverflow = mkAnd(
                    notNegativeValue,
                    mkOr(
                        mkBvSignedGreaterOrEqualExpr(sizeBits, sizeBitsUpperBound),
                        mkBvSignedLessOrEqualExpr(value, bvMaxValueUnsignedExtended(sizeBits)),
                    ),
                )
                checkOverflow(resNoOverflow, scope) ?: return

                value
            }
            is TvmArithmLogicalBitsizeInst -> {
                scope.consumeDefaultGas(stmt)

                val value = scope.takeLastIntOrThrowTypeError() ?: return
                val symbolicSizeBits = scope.calcOnState { makeSymbolicPrimitive(int257sort) }

                val disjArgs = mutableListOf(
                    mkAnd(signedIntegerFitsBits(value, 0u), symbolicSizeBits eq zeroValue)
                )
                var prevMinValue: UExpr<TvmInt257Sort> = bvMinValueSignedExtended(zeroValue)
                var prevMaxValue: UExpr<TvmInt257Sort> = bvMaxValueSignedExtended(zeroValue)
                for (sizeBits in 1..TvmContext.INT_BITS.toInt()) {
                    val minValue = bvMinValueSignedExtended(sizeBits.toBv257())
                    val maxValue = bvMaxValueSignedExtended(sizeBits.toBv257())
                    val smallestCond = mkOr(
                        mkBvSignedLessExpr(value, prevMinValue),
                        mkBvSignedGreaterExpr(value, prevMaxValue),
                    )
                    val arg = mkAnd(
                        smallestCond,
                        signedIntegerFitsBits(value, sizeBits.toUInt()),
                        symbolicSizeBits eq sizeBits.toBv257(),
                    )

                    disjArgs.add(arg)

                    prevMinValue = minValue
                    prevMaxValue = maxValue
                }

                scope.assert(
                    mkOr(disjArgs),
                    unsatBlock = { error("Statement: $stmt; cannot assert disjunction: $disjArgs") }
                ) ?: return

                symbolicSizeBits
            }
            is TvmArithmLogicalUbitsizeInst -> {
                scope.consumeDefaultGas(stmt)

                val value = scope.takeLastIntOrThrowTypeError() ?: return
                val notOutOfRangeExpr = mkBvSignedGreaterOrEqualExpr(value, zeroValue)
                checkOutOfRange(notOutOfRangeExpr, scope) ?: return

                val symbolicSizeBits = scope.calcOnState { makeSymbolicPrimitive(int257sort) }

                val disjArgs = mutableListOf(
                    mkAnd(unsignedIntegerFitsBits(value, 0u), symbolicSizeBits eq zeroValue)
                )
                var prevMaxValue: UExpr<TvmInt257Sort> = bvMaxValueUnsignedExtended(zeroValue)
                for (sizeBits in 1 until TvmContext.INT_BITS.toInt()) {
                    val maxValue = bvMaxValueUnsignedExtended(sizeBits.toBv257())
                    val smallestCond = mkBvSignedGreaterExpr(value, prevMaxValue)
                    val arg = mkAnd(
                        smallestCond,
                        unsignedIntegerFitsBits(value, sizeBits.toUInt()),
                        symbolicSizeBits eq sizeBits.toBv257(),
                    )

                    disjArgs.add(arg)

                    prevMaxValue = maxValue
                }

                scope.assert(
                    mkOr(disjArgs),
                    unsatBlock = { error("Statement: $stmt; cannot assert disjunction: $disjArgs") }
                ) ?: return

                symbolicSizeBits
            }
        }

        scope.doWithState {
            stack.addInt(result)
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitComparisonIntInst(scope: TvmStepScopeManager, stmt: TvmCompareIntInst) = with(ctx) {
        scope.consumeDefaultGas(stmt)

        when (stmt) {
            is TvmCompareIntEqintInst -> scope.doWithState {
                val x = takeLastIntOrThrowTypeError() ?: return@doWithState
                val y = stmt.y.toBv257()
                val expr = x eq y
                putBooleanAndToNewStmt(stmt, expr)
            }
            is TvmCompareIntNeqintInst -> scope.doWithState {
                val x = takeLastIntOrThrowTypeError() ?: return@doWithState
                val y = stmt.y.toBv257()
                val expr = (x eq y).not()
                putBooleanAndToNewStmt(stmt, expr)
            }
            is TvmCompareIntGtintInst -> scope.doWithState {
                val x = takeLastIntOrThrowTypeError() ?: return@doWithState
                val y = stmt.y.toBv257()
                val expr = mkBvSignedGreaterExpr(x, y)
                putBooleanAndToNewStmt(stmt, expr)
            }
            is TvmCompareIntLessintInst -> scope.doWithState {
                val x = takeLastIntOrThrowTypeError() ?: return@doWithState
                val y = stmt.y.toBv257()
                val expr = mkBvSignedLessExpr(x, y)
                putBooleanAndToNewStmt(stmt, expr)
            }
            is TvmCompareIntEqualInst -> scope.doWithState {
                val y = takeLastIntOrThrowTypeError() ?: return@doWithState
                val x = takeLastIntOrThrowTypeError() ?: return@doWithState
                val expr = x eq y
                putBooleanAndToNewStmt(stmt, expr)
            }
            is TvmCompareIntNeqInst -> scope.doWithState {
                val y = takeLastIntOrThrowTypeError() ?: return@doWithState
                val x = takeLastIntOrThrowTypeError() ?: return@doWithState
                val expr = (x eq y).not()
                putBooleanAndToNewStmt(stmt, expr)
            }
            is TvmCompareIntGreaterInst -> scope.doWithState {
                val y = takeLastIntOrThrowTypeError() ?: return@doWithState
                val x = takeLastIntOrThrowTypeError() ?: return@doWithState
                val expr = mkBvSignedGreaterExpr(x, y)
                putBooleanAndToNewStmt(stmt, expr)
            }
            is TvmCompareIntGeqInst -> scope.doWithState {
                val y = takeLastIntOrThrowTypeError() ?: return@doWithState
                val x = takeLastIntOrThrowTypeError() ?: return@doWithState
                val expr = mkBvSignedGreaterOrEqualExpr(x, y)
                putBooleanAndToNewStmt(stmt, expr)
            }
            is TvmCompareIntLessInst -> scope.doWithState {
                val y = takeLastIntOrThrowTypeError() ?: return@doWithState
                val x = takeLastIntOrThrowTypeError() ?: return@doWithState
                val expr = mkBvSignedLessExpr(x, y)
                putBooleanAndToNewStmt(stmt, expr)
            }
            is TvmCompareIntLeqInst -> scope.doWithState {
                val y = takeLastIntOrThrowTypeError() ?: return@doWithState
                val x = takeLastIntOrThrowTypeError() ?: return@doWithState
                val expr = mkBvSignedLessOrEqualExpr(x, y)
                putBooleanAndToNewStmt(stmt, expr)
            }
            is TvmCompareIntCmpInst -> scope.doWithState {
                val y = takeLastIntOrThrowTypeError() ?: return@doWithState
                val x = takeLastIntOrThrowTypeError() ?: return@doWithState
                doCmp(stmt, x, y)
            }
            is TvmCompareIntSgnInst -> scope.doWithState {
                val x = takeLastIntOrThrowTypeError() ?: return@doWithState
                doCmp(stmt, x, zeroValue)
            }
            is TvmCompareIntChknanInst -> TODO()
            is TvmCompareIntIsnanInst -> TODO()
        }
    }

    private fun TvmState.doCmp(stmt: TvmInst, x: UExpr<TvmInt257Sort>, y: UExpr<TvmInt257Sort>) {
        val value = with(ctx) {
            mkIte(x eq y, zeroValue, mkIte(mkBvSignedLessExpr(x, y), minusOneValue, oneValue))
        }
        stack.addInt(value)
        newStmt(stmt.nextStmt())
    }

    private fun TvmState.putBooleanAndToNewStmt(stmt: TvmInst, expr: UBoolExpr) {
        val value = ctx.mkIte(expr, ctx.trueValue, ctx.falseValue)
        stack.addInt(value)
        newStmt(stmt.nextStmt())
    }

    private fun visitComparisonOtherInst(scope: TvmStepScopeManager, stmt: TvmCompareOtherInst) = with(ctx) {
        when (stmt) {
            is TvmCompareOtherSdeqInst -> visitSliceDataEqInst(scope, stmt)
            is TvmCompareOtherSdemptyInst -> {
                scope.consumeDefaultGas(stmt)

                val slice = scope.calcOnState { takeLastSlice() }
                    ?: return scope.doWithState(throwTypeCheckError)

                val remainingBits = scope.calcOnState { getSliceRemainingBitsCount(slice) }
                val isEmpty = remainingBits eq zeroSizeExpr
                val result = isEmpty.toBv257Bool()

                scope.doWithState {
                    stack.addInt(result)
                    newStmt(stmt.nextStmt())
                }
            }
            is TvmCompareOtherSremptyInst -> {
                scope.consumeDefaultGas(stmt)

                val slice = scope.calcOnState { takeLastSlice() }
                if (slice == null) {
                    scope.doWithState(throwTypeCheckError)
                    return
                }

                val remainingRefs = scope.calcOnState { getSliceRemainingRefsCount(slice) }
                val isEmpty = remainingRefs eq zeroSizeExpr
                val result = isEmpty.toBv257Bool()

                scope.doWithState {
                    stack.addInt(result)
                    newStmt(stmt.nextStmt())
                }
            }
            is TvmCompareOtherSemptyInst -> {
                scope.consumeDefaultGas(stmt)

                val slice = scope.calcOnState { takeLastSlice() }
                if (slice == null) {
                    scope.doWithState(throwTypeCheckError)
                    return
                }

                val cell = scope.calcOnState { memory.readField(slice, sliceCellField, addressSort) }
                val dataPos = scope.calcOnState { memory.readField(slice, sliceDataPosField, sizeSort) }
                val refsPos = scope.calcOnState { memory.readField(slice, sliceRefPosField, sizeSort) }
                val dataLength = scope.calcOnState { memory.readField(cell, cellDataLengthField, sizeSort) }
                val refsLength = scope.calcOnState { memory.readField(cell, cellRefsLengthField, sizeSort) }

                val isRemainingDataEmptyConstraint = mkSizeGeExpr(dataPos, dataLength)
                val areRemainingRefsEmpty = mkSizeGeExpr(refsPos, refsLength)
                val result = mkAnd(isRemainingDataEmptyConstraint, areRemainingRefsEmpty).toBv257Bool()

                scope.doWithState {
                    stack.addInt(result)
                    newStmt(stmt.nextStmt())
                }
            }
            is TvmCompareOtherSdcnttrail0Inst -> {
                scope.consumeDefaultGas(stmt)

                val slice = scope.calcOnState { takeLastSlice() }
                if (slice == null) {
                    scope.doWithState(throwTypeCheckError)
                    return
                }

                // TODO make a real implementation
                val trailingZeroes = scope.calcOnState { makeSymbolicPrimitive(int257sort) }

                scope.doWithState {
                    scope.assert(
                        mkBvSignedGreaterOrEqualExpr(trailingZeroes, zeroValue),
                        unsatBlock = { error("Cannot make trailing zeroes >= 0") }
                    ) ?: return@doWithState

                    stack.addInt(trailingZeroes)
                    newStmt(stmt.nextStmt())
                }
            }

            else -> TODO("$stmt")
        }
    }

    private fun visitSliceDataEqInst(scope: TvmStepScopeManager, stmt: TvmCompareOtherSdeqInst) = with(ctx) {
        scope.consumeDefaultGas(stmt)

        val (slice1, slice2) = scope.calcOnState { takeLastSlice() to takeLastSlice() }
        if (slice1 == null || slice2 == null) {
            scope.doWithState(throwTypeCheckError)
            return
        }

        val constraint = scope.slicesAreEqual(slice1, slice2)
            ?: return
        val result = constraint.toBv257Bool()

        scope.doWithState {
            stack.addInt(result)
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitTvmSaveControlFlowInst(
        scope: TvmStepScopeManager,
        stmt: TvmContRegistersInst
    ) {
        when (stmt) {
            is TvmContRegistersPushctrInst -> visitTvmPushCtrInst(scope, stmt)
            is TvmContRegistersPopctrInst -> visitTvmPopCtrInst(scope, stmt)
            is TvmContRegistersSetcontctrInst -> visitSetContCtr(scope, stmt)
            is TvmContRegistersSaveInst -> visitSaveInst(scope, stmt)
            is TvmContRegistersSamealtInst -> {
                scope.consumeDefaultGas(stmt)

                scope.doWithState {
                    val registers = registersOfCurrentContract
                    registers.c1 = C1Register(registers.c0.value)
                    newStmt(stmt.nextStmt())
                }
            }
            is TvmContRegistersSamealtsaveInst -> {
                scope.consumeDefaultGas(stmt)

                scope.doWithState {
                    val registers = registersOfCurrentContract
                    val c1 = registers.c1.value

                    registers.c0 = C0Register(registers.c0.value.defineC1(c1))
                    registers.c1 = C1Register(registers.c0.value)
                    newStmt(stmt.nextStmt())
                }
            }
            is TvmContRegistersComposInst -> {
                scope.consumeDefaultGas(stmt)

                val next = scope.calcOnState { stack.takeLastContinuation() }
                    ?: return scope.doWithState(ctx.throwTypeCheckError)
                val cont = scope.calcOnState { stack.takeLastContinuation() }
                    ?: return scope.doWithState(ctx.throwTypeCheckError)

                scope.doWithState {
                    stack.addContinuation(cont.defineC0(next))
                    newStmt(stmt.nextStmt())
                }
            }
            is TvmContRegistersComposaltInst -> {
                scope.consumeDefaultGas(stmt)

                val next = scope.calcOnState { stack.takeLastContinuation() }
                    ?: return scope.doWithState(ctx.throwTypeCheckError)
                val cont = scope.calcOnState { stack.takeLastContinuation() }
                    ?: return scope.doWithState(ctx.throwTypeCheckError)

                scope.doWithState {
                    stack.addContinuation(cont.defineC1(next))
                    newStmt(stmt.nextStmt())
                }
            }
            is TvmContRegistersComposbothInst -> {
                scope.consumeDefaultGas(stmt)

                val next = scope.calcOnState { stack.takeLastContinuation() }
                    ?: return scope.doWithState(ctx.throwTypeCheckError)
                val cont = scope.calcOnState { stack.takeLastContinuation() }
                    ?: return scope.doWithState(ctx.throwTypeCheckError)

                scope.doWithState {
                    stack.addContinuation(cont.defineC0(next).defineC1(next))
                    newStmt(stmt.nextStmt())
                }
            }
            else -> TODO("$stmt")
        }
    }

    private fun visitSaveInst(scope: TvmStepScopeManager, stmt: TvmContRegistersSaveInst) = scope.doWithState {
        scope.consumeDefaultGas(stmt)

        val registers = registersOfCurrentContract
        val c0 = registers.c0.value
        val registerIndex = stmt.i

        val updatedC0 = when (registerIndex) {
            0 -> c0.defineC0(c0)
            1 -> {
                val c1 = registers.c1.value
                c0.defineC1(c1)
            }
            2 -> {
                val c2 = registers.c2.value
                c0.defineC2(c2)
            }
            3 -> {
                val c3 = registers.c3
                c0.defineC3(c3)
            }
            4 -> {
                val c4 = registers.c4.value.value
                c0.defineC4(c4)
            }
            5 -> {
                val c5 = registers.c5.value.value
                c0.defineC5(c5)
            }
            7 -> {
                val c7 = registers.c7.value
                c0.defineC7(c7)
            }
            else -> TODO("Not yet implemented: $stmt")
        }

        registers.c0 = C0Register(updatedC0)
        newStmt(stmt.nextStmt())
    }

    private fun visitSetContCtr(scope: TvmStepScopeManager, stmt: TvmContRegistersSetcontctrInst) = scope.doWithStateCtx {
        scope.consumeDefaultGas(stmt)

        val cont = stack.takeLastContinuation()
            ?: return@doWithStateCtx throwTypeCheckError(this)

        val updatedCont = when (stmt.i) {
            0 -> {
                val contToStore = stack.takeLastContinuation()
                    ?: return@doWithStateCtx throwTypeCheckError(this)
                cont.defineC0(contToStore)
            }
            1 -> {
                val contToStore = stack.takeLastContinuation()
                    ?: return@doWithStateCtx throwTypeCheckError(this)
                cont.defineC1(contToStore)
            }
            2 -> {
                val contToStore = stack.takeLastContinuation()
                    ?: return@doWithStateCtx throwTypeCheckError(this)
                cont.defineC2(contToStore)
            }
            3 -> {
                val contToStore = stack.takeLastContinuation()
                    ?: return@doWithStateCtx throwTypeCheckError(this)
                val oldC3 = registersOfCurrentContract.c3

                require(oldC3.value == contToStore) {
                    "General case is not supported: $stmt"
                }

                cont.defineC3(oldC3)
            }
            4 -> {
                val cell = takeLastCell()
                    ?: return@doWithStateCtx throwTypeCheckError(this)

                cont.defineC4(cell)
            }
            5 -> {
                val cell = takeLastCell()
                    ?: return@doWithStateCtx throwTypeCheckError(this)

                cont.defineC5(cell)
            }
            7 -> {
                val tuple = scope.takeLastTuple()
                    ?: return@doWithStateCtx throwTypeCheckError(this)

                cont.defineC7(tuple)
            }
            else -> TODO("Not yet implemented: $stmt")
        }

        stack.addContinuation(updatedCont)
        newStmt(stmt.nextStmt())
    }

    private fun visitTvmPopCtrInst(scope: TvmStepScopeManager, stmt: TvmContRegistersPopctrInst) = scope.doWithStateCtx {
        scope.consumeDefaultGas(stmt)

        val registerIndex = stmt.i
        val registers = registersOfCurrentContract

        when (registerIndex) {
            0 -> {
                val cont = stack.takeLastContinuation()
                    ?: return@doWithStateCtx throwTypeCheckError(this)
                registers.c0 = C0Register(cont)
            }
            1 -> {
                val cont = stack.takeLastContinuation()
                    ?: return@doWithStateCtx throwTypeCheckError(this)
                registers.c1 = C1Register(cont)
            }
            2 -> {
                val cont = stack.takeLastContinuation()
                    ?: return@doWithStateCtx throwTypeCheckError(this)
                registers.c2 = C2Register(cont)
            }
            3 -> {
                val cont = stack.takeLastContinuation()
                    ?: return@doWithStateCtx throwTypeCheckError(this)
                if (
                    cont !is TvmOrdContinuation ||  // support only ordinary continuations
                    cont.isPartiallyExecuted() ||
                    cont.sourceCell == null  // we can extract TsaContractCode only if continuation has raw cell representation
                ) {
                    TODO("General case of $stmt not supported")
                }
                val parent = registers.c3.code
                val tvmCode = disassembleCell(cont.sourceCell)
                val tsaCode = TsaContractCode.construct(tvmCode, cont.sourceCell, parent)
                registers.c3 = C3Register(TvmOrdContinuation(tsaCode.mainMethod, tsaCode.codeCell), tsaCode)
            }
            4 -> {
                val newData = takeLastCell()
                    ?: return@doWithStateCtx throwTypeCheckError(this)

                registers.c4 = C4Register(TvmCellValue(newData))
            }
            5 -> {
                val newData = takeLastCell()
                    ?: return@doWithStateCtx throwTypeCheckError(this)

                registers.c5 = C5Register(TvmCellValue(newData))
            }
            7 -> {
                val newC7 = scope.takeLastTuple()
                    ?: return@doWithStateCtx throwTypeCheckError(this)

                require(newC7 is TvmStackTupleValueConcreteNew) {
                    TODO("Support non-concrete tuples")
                }

                registers.c7 = C7Register(newC7)
            }
            else -> TODO("Not yet implemented: $stmt")
        }

        newStmt(stmt.nextStmt())
    }

    private fun TvmOrdContinuation.isPartiallyExecuted(): Boolean =
        stack != null || savelist != TvmRegisterSavelist.EMPTY || nargs != null || stmt.location.index != 0

    private fun visitTvmPushCtrInst(scope: TvmStepScopeManager, stmt: TvmContRegistersPushctrInst) {
        scope.consumeDefaultGas(stmt)

        scope.doWithState {
            // TODO use it!
            val registerIndex = stmt.i
            val registers = registersOfCurrentContract

            // TODO should we use real persistent or always consider it fully symbolic?
//            val data = registers.c4?.value?.value?.toSymbolic(scope) ?: mkSymbolicCell(scope)
            when (registerIndex) {
                0 -> {
                    stack.addContinuation(registers.c0.value)
                    newStmt(stmt.nextStmt())
                }
                1 -> {
                    val c1 = registers.c1

                    stack.addContinuation(c1.value)
                    newStmt(stmt.nextStmt())
                }
                2 -> {
                    val c2 = registers.c2

                    stack.addContinuation(c2.value)
                    newStmt(stmt.nextStmt())
                }
                3 -> {
                    val cont = registers.c3.value
                    stack.addContinuation(cont)
                    newStmt(stmt.nextStmt())
                }
                4 -> {
                    val data = registers.c4.value.value
                    scope.addOnStack(data, TvmCellType)
                    newStmt(stmt.nextStmt())
                }
                5 -> {
                    val cell = scope.calcOnState { registers.c5.value.value }

                    scope.addOnStack(cell, TvmCellType)
                    newStmt(stmt.nextStmt())
                }
                7 -> {
                    stack.addTuple(registers.c7.value)
                    newStmt(stmt.nextStmt())
                }
                else -> TODO("Not yet implemented: $stmt")
            }

        }
    }

    private fun visitTvmBasicControlFlowInst(
        scope: TvmStepScopeManager,
        stmt: TvmContBasicInst
    ) {
        when (stmt) {
            is TvmContBasicExecuteInst -> {
                scope.consumeDefaultGas(stmt)

                val continuationValue = scope.calcOnState { stack.takeLastContinuation() }
                    ?: return scope.doWithState(ctx.throwTypeCheckError)

                scope.callContinuation(stmt, continuationValue)
            }
            is TvmContBasicRetInst -> {
                scope.consumeDefaultGas(stmt)

                scope.returnFromContinuation()
            }
            is TvmContBasicRetaltInst -> {
                scope.consumeDefaultGas(stmt)

                scope.returnAltFromContinuation()
            }
            is TvmContBasicCallrefInst -> {
                scope.doWithState { consumeGas(126) } // TODO complex gas 126/51

                val continuationValue = TvmOrdContinuation(TvmLambda(stmt.c.list.toMutableList()), stmt.c.raw)

                scope.callContinuation(stmt, continuationValue)
            }
            is TvmContBasicCallxargsVarInst -> {
                scope.consumeDefaultGas(stmt)
                checkArgument(stmt.p, 0..15, stmt)
                doCallxArgs(scope, stmt, passArgs = stmt.p, returnArgs = null)
            }
            is TvmContBasicCallxargsInst -> {
                scope.consumeDefaultGas(stmt)
                checkArgument(stmt.p, 0..15, stmt)
                checkArgument(stmt.r, 0..15, stmt)
                doCallxArgs(scope, stmt, passArgs = stmt.p, returnArgs = stmt.r)
            }
            is TvmContBasicCallxvarargsInst -> {
                scope.consumeDefaultGas(stmt)
                val symbolicR = scope.takeLastIntOrThrowTypeError()
                    ?: return
                val symbolicP = scope.takeLastIntOrThrowTypeError()
                    ?: return
                val r = symbolicR.extractConcrete(stmt).takeIf { it != -1 }
                val p = symbolicP.extractConcrete(stmt).takeIf { it != -1 }
                checkArgument(p, 0..254, stmt)
                checkArgument(r, 0..254, stmt)
                doCallxArgs(scope, stmt, passArgs = p, returnArgs = r)
            }
            is TvmContBasicJmpxInst -> {
                scope.consumeDefaultGas(stmt)

                val continuationValue = scope.calcOnState { stack.takeLastContinuation() }
                    ?: return scope.doWithState(ctx.throwTypeCheckError)

                scope.jumpToContinuation(continuationValue)
            }
            else -> TODO("$stmt")
        }
    }

    private fun checkArgument(arg: Int?, range: IntRange, stmt: TvmInst) {
        check(arg == null || arg in range) {
            "Unexpected number of arguments for $stmt: $arg"
        }
    }

    private fun doCallxArgs(
        scope: TvmStepScopeManager,
        stmt: TvmContBasicInst,
        passArgs: Int?,
        returnArgs: Int?,
    ) {
        val continuationValue = scope.calcOnState { stack.takeLastContinuation() }
            ?: return scope.doWithState(ctx.throwTypeCheckError)

        scope.callContinuationComplex(stmt, continuationValue, passArgs?.toUInt(), returnArgs?.toUInt())
    }

    private fun visitTvmConditionalControlFlowInst(
        scope: TvmStepScopeManager,
        stmt: TvmContConditionalInst
    ) {
        when (stmt) {
            is TvmContConditionalIfretInst -> visitIfRet(scope, stmt, invertCondition = false)
            is TvmContConditionalIfnotretInst -> visitIfRet(scope, stmt, invertCondition = true)
            is TvmContConditionalIfInst -> visitIf(scope, stmt, invertCondition = false)
            is TvmContConditionalIfnotInst -> visitIf(scope, stmt, invertCondition = true)
            is TvmContConditionalIfrefInst -> visitIfRef(scope, stmt, stmt.c, invertCondition = false)
            is TvmContConditionalIfnotrefInst -> visitIfRef(scope, stmt, stmt.c, invertCondition = true)
            is TvmContConditionalIfjmpInst -> visitIfJmp(scope, stmt, invertCondition = false)
            is TvmContConditionalIfnotjmpInst -> visitIfJmp(scope, stmt, invertCondition = true)
            is TvmContConditionalIfjmprefInst -> visitIfJmpRef(scope, stmt, stmt.c, invertCondition = false)
            is TvmContConditionalIfnotjmprefInst -> visitIfJmpRef(scope, stmt, stmt.c, invertCondition = true)
            is TvmContConditionalIfelseInst -> visitIfElseInst(scope, stmt)
            is TvmContConditionalIfrefelserefInst -> visitIfRefElseRefInst(scope, stmt)
            is TvmContConditionalIfrefelseInst -> visitIfRefElseInst(scope, stmt)
            is TvmContConditionalIfelserefInst -> visitIfElseRefInst(scope, stmt)
            is TvmContConditionalCondselInst -> visitCondsel(scope, stmt)
            else -> TODO("$stmt")
        }
    }

    private fun visitCondsel(scope: TvmStepScopeManager, stmt: TvmContConditionalCondselInst) {
        scope.consumeDefaultGas(stmt)
        val y = scope.calcOnState { stack.takeLastEntry() }
        val x = scope.calcOnState { stack.takeLastEntry() }
        val cond = scope.takeLastIntOrThrowTypeError()
            ?: return
        val neqZero = scope.doWithCtx { mkEq(cond, zeroValue).not() }
        scope.fork(
            neqZero,
            falseStateIsExceptional = false,
            blockOnFalseState = {
                stack.addStackEntry(y)
                newStmt(stmt.nextStmt())
            },
            blockOnTrueState = {
                stack.addStackEntry(x)
                newStmt(stmt.nextStmt())
            }
        )
    }

    private fun visitIfRet(scope: TvmStepScopeManager, stmt: TvmInst, invertCondition: Boolean) {
        scope.consumeDefaultGas(stmt)

        val operand = scope.takeLastIntOrThrowTypeError() ?: return

        with(ctx) {
            val neqZero = if (!invertCondition) {
                mkEq(operand, zeroValue).not()
            } else {
                mkEq(operand, zeroValue)
            }

            scope.fork(
                neqZero,
                falseStateIsExceptional = false,
                blockOnFalseState = {
                    newStmt(stmt.nextStmt())
                }
            ) ?: return@with

            // TODO check NaN for integer overflow exception

            scope.returnFromContinuation()
        }
    }

    private fun visitIf(scope: TvmStepScopeManager, stmt: TvmContConditionalInst, invertCondition: Boolean) {
        scope.consumeDefaultGas(stmt)

        val continuation = scope.calcOnState { stack.takeLastContinuation() }
            ?: return scope.doWithState(ctx.throwTypeCheckError)

        return scope.doIf(continuation, stmt, invertCondition, isJmp = false)
    }

    private fun visitIfRef(scope: TvmStepScopeManager, stmt: TvmContConditionalInst, ref: TvmInstList, invertCondition: Boolean) {
        val continuation = scope.calcOnState {
            consumeGas(26) // TODO complex gas "26/126/51"

            TvmOrdContinuation(TvmLambda(ref.list.toMutableList()), ref.raw)
        }
        return scope.doIf(continuation, stmt, invertCondition, isJmp = false)
    }

    private fun visitIfElseInst(scope: TvmStepScopeManager, stmt: TvmContConditionalIfelseInst) {
        scope.consumeDefaultGas(stmt)

        scope.doWithState {
            val secondContinuation = stack.takeLastContinuation()
                ?: return@doWithState ctx.throwTypeCheckError(this)
            val firstContinuation = stack.takeLastContinuation()
                ?: return@doWithState ctx.throwTypeCheckError(this)

            scope.doIfElse(firstContinuation, secondContinuation, stmt)
        }
    }

    private fun visitIfRefElseRefInst(scope: TvmStepScopeManager, stmt: TvmContConditionalIfrefelserefInst) {
        scope.doWithState {
            consumeGas(51) // TODO complex gas "126/51"

            val firstContinuation = TvmOrdContinuation(TvmLambda(stmt.c1.list.toMutableList()), stmt.c1.raw)
            val secondContinuation = TvmOrdContinuation(TvmLambda(stmt.c2.list.toMutableList()), stmt.c2.raw)

            scope.doIfElse(firstContinuation, secondContinuation, stmt)
        }
    }

    private fun visitIfRefElseInst(scope: TvmStepScopeManager, stmt: TvmContConditionalIfrefelseInst) {
        scope.doWithState {
            consumeGas(26) // TODO complex gas "26/126/51"

            val firstContinuation = TvmOrdContinuation(TvmLambda(stmt.c.list.toMutableList()), stmt.c.raw)
            val secondContinuation = stack.takeLastContinuation()
                ?: return@doWithState ctx.throwTypeCheckError(this)

            scope.doIfElse(firstContinuation, secondContinuation, stmt)
        }
    }

    private fun visitIfElseRefInst(scope: TvmStepScopeManager, stmt: TvmContConditionalIfelserefInst) {
        scope.doWithState {
            consumeGas(26) // TODO complex gas "26/126/51"

            val firstContinuation = stack.takeLastContinuation()
                ?: return@doWithState ctx.throwTypeCheckError(this)
            val secondContinuation = TvmOrdContinuation(TvmLambda(stmt.c.list.toMutableList()), stmt.c.raw)

            scope.doIfElse(firstContinuation, secondContinuation, stmt)
        }
    }

    private fun TvmStepScopeManager.doIf(
        continuation: TvmContinuation,
        stmt: TvmInst,
        invertCondition: Boolean,
        isJmp: Boolean,
    ) = with(ctx) {
        val flag = takeLastIntOrThrowTypeError() ?: return
        val invertedCondition = (flag eq zeroValue).let {
            if (invertCondition) it else it.not()
        }

        fork(
            invertedCondition,
            falseStateIsExceptional = false,
            blockOnFalseState = {
                newStmt(stmt.nextStmt())
            }
        ) ?: return@with

        if (isJmp) {
            jumpToContinuation(continuation)
        } else {
            callContinuation(stmt, continuation)
        }
    }

    private fun TvmStepScopeManager.doIfElse(
        firstContinuation: TvmContinuation,
        secondContinuation: TvmContinuation,
        stmt: TvmInst
    ) {
        val flag = takeLastIntOrThrowTypeError() ?: return
        doWithStateCtx {
            val ifConstraint = mkEq(flag, zeroValue).not()

            fork(
                ifConstraint,
                falseStateIsExceptional = false,
                blockOnTrueState = {
                    // TODO really?
//                        registers = continuation.registers
//                        stack = continuation.stack

                    newStmt(TsaArtificialExecuteContInst(firstContinuation, stmt.location))
                },
                blockOnFalseState = {
                    // TODO really?
//                        registers = continuation.registers
//                        stack = continuation.stack

                    newStmt(TsaArtificialExecuteContInst(secondContinuation, stmt.location))
                }
            )
        }
    }


    private fun visitIfJmp(scope: TvmStepScopeManager, stmt: TvmContConditionalInst, invertCondition: Boolean) {
        scope.consumeDefaultGas(stmt)

        val continuation = scope.calcOnState { stack.takeLastContinuation() }
            ?: return scope.doWithState(ctx.throwTypeCheckError)

        scope.doIf(continuation, stmt, invertCondition, isJmp = true)
    }

    private fun visitIfJmpRef(
        scope: TvmStepScopeManager,
        stmt: TvmContConditionalInst,
        ref: TvmInstList,
        invertCondition: Boolean
    ) {
        scope.doWithState {
            consumeGas(26) // TODO complex gas "26/126/51"
        }

        val continuation = scope.calcOnState {
            TvmOrdContinuation(TvmLambda(ref.list.toMutableList()), ref.raw)
        }
        scope.doIf(continuation, stmt, invertCondition, isJmp = true)
    }

    private fun visitTvmDictionaryJumpInst(scope: TvmStepScopeManager, stmt: TvmContDictInst) {
        scope.consumeDefaultGas(stmt)

        when (stmt) {
            is TvmContDictCalldictInst -> {
                performCall(scope, stmt, stmt.n)
            }
            is TvmContDictCalldictLongInst -> {
                performCall(scope, stmt, stmt.n)
            }
            is TvmContDictPreparedictInst -> {
                val contractCode = scope.calcOnState {
                    registersOfCurrentContract.c3.code
                }

                val continuation = TvmOrdContinuation(contractCode.mainMethod, contractCode.codeCell)

                scope.doWithStateCtx {
                    stack.addInt(stmt.n.toBv257())
                    stack.addContinuation(continuation)
                    newStmt(stmt.nextStmt())
                }
            }
            else -> TODO("Unknown stmt: $stmt")
        }
    }

    private fun extractMethod(scope: TvmStepScopeManager, methodIdInt: Int): TvmMethod? {
        val methodId = methodIdInt.toMethodId()
        val contractCode = scope.calcOnState {
            registersOfCurrentContract.c3.code
        }
        val nextMethod = contractCode.methods[methodId]
            ?: error("Unknown method with id $methodId")
        val methodRecursionDepth = scope.calcOnState { getMethodRecursionDepth(nextMethod.id) }

        if (methodRecursionDepth >= ctx.tvmOptions.maxRecursionDepth) {
            logger.warn { "Maximum recursion depth of method $methodId is reached, dropping the state" }
            scope.killCurrentState()
                ?: return null
        }

        return nextMethod
    }

    private fun performCall(scope: TvmStepScopeManager, stmt: TvmContDictInst, methodIdInt: Int) {
        tsaCheckerFunctionsInterpreter.doTSACheckerOperation(scope, stmt, methodIdInt)
            ?: return

        val nextMethod = extractMethod(scope, methodIdInt)
            ?: return

        scope.callMethod(stmt, nextMethod)
    }

    private fun visitDictControlFlowInst(
        scope: TvmStepScopeManager,
        stmt: TvmDictSpecialInst
    ) {
        scope.consumeDefaultGas(stmt)

        when (stmt) {
            is TvmDictSpecialDictigetjmpzInst -> {
                val contractCode = scope.calcOnState {
                    registersOfCurrentContract.c3.code
                }

                require(stmt.location is TvmMainMethodLocation && contractCode.methods.isNotEmpty()) {
                    "The general case is not supported: $stmt"
                }

                val methodIdSymbolic = scope.takeLastIntOrThrowTypeError()
                val methodId = methodIdSymbolic?.bigIntValue()
                    ?: return

                // This is for (hypothetical) case when methodId doesn't fit into Int.
                // In this case, this is not a checker function.
                if (methodId <= Int.MAX_VALUE.toBigInteger()) {
                    tsaCheckerFunctionsInterpreter.doTSACheckerOperation(scope, stmt, methodId.intValueExact())
                        ?: return
                }

                val method = contractCode.methods[methodId] ?: run {
                    scope.addOnStack(methodIdSymbolic, TvmIntegerType)
                    scope.doWithState { newStmt(stmt.nextStmt()) }
                    return
                }

                // The remainder of the previous current continuation cc is discarded.
                scope.jumpToContinuation(TvmOrdContinuation(method, sourceCell = null))
            }

            is TvmDictSpecialDictpushconstInst -> {

                val contractCode = scope.calcOnState {
                    registersOfCurrentContract.c3.code
                }

                if (stmt.location !is TvmMainMethodLocation || contractCode.methods.isEmpty()) {
                    TODO("General case of TvmDictSpecialDictpushconstInst not supported")
                }

                scope.doWithState { newStmt(stmt.nextStmt()) }
            }
            else -> TODO("$stmt")
        }
    }

    private fun visitDebugInst(scope: TvmStepScopeManager, stmt: TvmDebugInst) {
        scope.consumeDefaultGas(stmt)

        // Do nothing
        scope.doWithState { newStmt(stmt.nextStmt()) }
    }

    private fun visitCodepageInst(scope: TvmStepScopeManager, stmt: TvmCodepageInst) {
        scope.consumeDefaultGas(stmt)

        // Do nothing
        scope.doWithState { newStmt(stmt.nextStmt()) }
    }

    private fun UExpr<TvmInt257Sort>.extractConcrete(inst: TvmInst): Int {
        if (this !is KInterpretedValue)
            TODO("symbolic value in $inst")
        return intValue()
    }

    private fun TvmState.getMethodRecursionDepth(methodId: MethodId): Int =
        callStack.count { it.method.extractMethodIdOrNull() == methodId }
}
