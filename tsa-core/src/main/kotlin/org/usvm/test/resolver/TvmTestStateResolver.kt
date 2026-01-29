package org.usvm.test.resolver

import io.ksmt.expr.KBitVecValue
import io.ksmt.utils.BvUtils.toBigIntegerSigned
import kotlinx.collections.immutable.persistentListOf
import org.ton.TlbBitArrayByRef
import org.ton.TlbBitArrayOfConcreteSize
import org.ton.TlbBuiltinLabel
import org.ton.TlbIntegerLabel
import org.ton.TlbIntegerLabelOfConcreteSize
import org.ton.TlbResolvedBuiltinLabel
import org.ton.TvmParameterInfo
import org.ton.bytecode.ADDRESS_PARAMETER_IDX
import org.ton.bytecode.BALANCE_PARAMETER_IDX
import org.ton.bytecode.CONFIG_PARAMETER_IDX
import org.ton.bytecode.TIME_PARAMETER_IDX
import org.ton.bytecode.TvmArtificialInst
import org.ton.bytecode.TvmCodeBlock
import org.ton.bytecode.TvmInst
import org.usvm.NULL_ADDRESS
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.readField
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.isFalse
import org.usvm.isStatic
import org.usvm.isTrue
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.MAX_DATA_LENGTH
import org.usvm.machine.TvmContext.Companion.dictKeyLengthField
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.intValue
import org.usvm.machine.interpreter.inputdict.InputDict
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.DictId
import org.usvm.machine.state.TvmCellRefsRegionValueInfo
import org.usvm.machine.state.TvmRefsMemoryRegion
import org.usvm.machine.state.TvmResult
import org.usvm.machine.state.TvmStack
import org.usvm.machine.state.TvmStack.TvmStackTupleValue
import org.usvm.machine.state.TvmStack.TvmStackTupleValueConcreteNew
import org.usvm.machine.state.TvmStack.TvmStackValue
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmStructuralError
import org.usvm.machine.state.allocatedDictContainsKey
import org.usvm.machine.state.calcConsumedGas
import org.usvm.machine.state.calcPhaseConsumedGas
import org.usvm.machine.state.dictGetValue
import org.usvm.machine.state.dictKeyEntries
import org.usvm.machine.state.ensureSymbolicBuilderInitialized
import org.usvm.machine.state.ensureSymbolicCellInitialized
import org.usvm.machine.state.ensureSymbolicSliceInitialized
import org.usvm.machine.state.input.RecvExternalInput
import org.usvm.machine.state.input.RecvInternalInput
import org.usvm.machine.state.input.TvmStackInput
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.messages.MessageAsStackArguments
import org.usvm.machine.state.messages.MessageSource
import org.usvm.machine.state.messages.ReceivedMessage
import org.usvm.machine.state.tvmCellRefsRegion
import org.usvm.machine.types.TvmBuilderType
import org.usvm.machine.types.TvmCellDataBitArrayRead
import org.usvm.machine.types.TvmCellDataCoinsRead
import org.usvm.machine.types.TvmCellDataIntegerRead
import org.usvm.machine.types.TvmCellDataMsgAddrRead
import org.usvm.machine.types.TvmCellDataTypeRead
import org.usvm.machine.types.TvmCellMaybeConstructorBitRead
import org.usvm.machine.types.TvmDataCellLoadedTypeInfo
import org.usvm.machine.types.TvmDataCellType
import org.usvm.machine.types.TvmDictCellType
import org.usvm.machine.types.TvmFinalReferenceType
import org.usvm.machine.types.TvmModel
import org.usvm.machine.types.TvmReadingOfUnexpectedType
import org.usvm.machine.types.TvmReadingOutOfSwitchBounds
import org.usvm.machine.types.TvmReadingSwitchWithUnexpectedType
import org.usvm.machine.types.TvmSliceType
import org.usvm.machine.types.TvmType
import org.usvm.machine.types.TvmUnexpectedDataReading
import org.usvm.machine.types.TvmUnexpectedEndOfReading
import org.usvm.machine.types.TvmUnexpectedRefReading
import org.usvm.machine.types.dp.getDefaultDict
import org.usvm.machine.types.getPossibleTypes
import org.usvm.machine.types.memory.readInModelFromTlbFields
import org.usvm.memory.UMemory
import org.usvm.sizeSort
import org.usvm.solver.UExprTranslator
import java.math.BigInteger

class TvmTestStateResolver(
    private val ctx: TvmContext,
    val model: TvmModel,
    val state: TvmState,
    private val performAdditionalChecks: Boolean = false, // for testing
) {
    private val stack: TvmStack
        get() = state.rootStack

    private val memory: UMemory<TvmType, TvmCodeBlock>
        get() = state.memory

    private val resolvedCache = mutableMapOf<UConcreteHeapAddress, TvmTestCellValue>()

    private val labelMapper
        get() = state.dataCellInfoStorage.mapper

    private val constraintVisitor = ConstraintsVisitor(ctx)

    fun <T : USort> eval(expr: UExpr<T>) = model.eval(expr)

    init {
        // collect info about all constraints in state
        state.pathConstraints.constraints(constraintVisitor).toList().forEach {
            if (eval(it).isFalse) {
                error("Resolving contradicting state!")
            }
        }
    }

    fun resolveEvents(): List<TvmMessageDrivenContractExecutionTestEntry> =
        state.eventsLog.map { entry ->

            if (ctx.tvmOptions.performAdditionalChecksWhileResolving) {
                val expectedContract = entry.contractId
                state.gasUsageHistory.subList(entry.executionBegin, entry.executionEnd).forEach {
                    check(it.first == expectedContract) {
                        "Instruction with wrong contract in event"
                    }
                }
            }

            TvmMessageDrivenContractExecutionTestEntry(
                id = entry.id,
                executionBegin = entry.executionBegin,
                executionEnd = entry.executionEnd,
                contractId = entry.contractId,
                incomingMessage = resolveReceivedMessage(entry.incomingMessage),
                computePhaseResult = resolveResultStackImpl(entry.computePhaseResult),
                actionPhaseResult = entry.actionPhaseResult?.let { resolveResultStackImpl(it) },
                gasUsage = resolvePhaseGasUsage(entry.executionBegin, entry.executionEnd),
                computeFee = resolveInt257(entry.computeFee),
                eventTime = resolveInt257(entry.eventTime),
            )
        }

    private fun resolveRecvInternalInput(input: RecvInternalInput): TvmTestInput.RecvInternalInput =
        TvmTestInput.RecvInternalInput(
            srcAddress = resolveSlice(input.srcAddressSlice),
            msgValue = resolveInt257(input.msgValue),
            msgBody = resolveSlice(input.msgBodySliceMaybeBounced),
            bounce = resolveBool(input.bounce),
            bounced = resolveBool(input.bounced),
            ihrDisabled = resolveBool(input.ihrDisabled),
            ihrFee = resolveInt257(input.ihrFee),
            fwdFee = resolveInt257(input.fwdFee),
            createdLt = resolveInt257(input.createdLt),
            createdAt = resolveInt257(input.createdAt),
        )

    private fun resolveRecvExternalInput(input: RecvExternalInput): TvmTestInput.RecvExternalInput =
        TvmTestInput.RecvExternalInput(
            msgBody = resolveSlice(input.msgBodySliceMaybeBounced),
            wasAccepted = state.acceptedInputs.contains(input),
        )

    fun resolveInput(): TvmTestInput =
        when (val input = state.initialInput) {
            is TvmStackInput -> TvmTestInput.StackInput(resolveStackInput())
            is RecvInternalInput -> resolveRecvInternalInput(input)
            is RecvExternalInput -> resolveRecvExternalInput(input)
        }

    private fun resolveBool(boolExpr: UBoolExpr): Boolean = model.eval(boolExpr).isTrue

    private fun resolveStackInput(): List<TvmTestValue> =
        stack.inputValues
            .filterNotNull()
            .map {
                resolveStackValue(it)
            }.reversed()

    fun resolveFetchedValues(): Map<Int, TvmTestValue> =
        state.fetchedValues.mapValues { (index, stackEntry) ->
            val value =
                stackEntry.cell(stack)
                    ?: error("Fetched value $index was expected to be concrete stack entry, but got $stackEntry")

            resolveStackValue(value)
        }

    fun resolveInitialData(): Map<ContractId, TvmTestCellValue> =
        state.contractIdToInitialData.entries.associate { (key, value) ->
            key to resolveCell(value.persistentData)
        }

    fun resolveConfig(): TvmTestDictCellValue {
        val config = getInitialRootContractParam(CONFIG_PARAMETER_IDX)

        return (resolveStackValue(config) as? TvmTestDictCellValue)
            ?: error("Unexpected config type")
    }

    fun resolveContractAddress(): TvmTestDataCellValue {
        val address = getInitialRootContractParam(ADDRESS_PARAMETER_IDX)

        return (resolveStackValue(address) as? TvmTestDataCellValue)
            ?: error("Unexpected address type")
    }

    fun resolveInitialContractState(contract: ContractId): TvmContractState {
        val balance =
            getInitialContractParam(contract, BALANCE_PARAMETER_IDX)
                .tupleValue
                ?.get(0, stack)
                ?.cell(stack)
                ?: error("Unexpected contract balance")

        val c7Balance =
            (resolveStackValue(balance) as? TvmTestIntegerValue)
                ?: error("Unexpected balance type")

        val symbolicData =
            state.contractIdToInitialData[contract]
                ?: error("Contract $contract initial data not found")

        val data = resolveCell(symbolicData.persistentData)

        return TvmContractState(data, c7Balance)
    }

    fun resolveTime(): TvmTestIntegerValue {
        val now = getInitialRootContractParam(TIME_PARAMETER_IDX)

        return (resolveStackValue(now) as? TvmTestIntegerValue)
            ?: error("Unexpected address type")
    }

    private fun getInitialRootContractParam(idx: Int): TvmStackValue {
        val value = state.rootInitialData.firstElementOfC7[idx, stack]

        return value.cell(stack)
            ?: error("Unexpected $idx parameter value: $value")
    }

    private fun getInitialContractParam(
        contract: ContractId,
        idx: Int,
    ): TvmStackValue {
        val initialData =
            state.contractIdToInitialData[contract]
                ?: error("Contract $contract not found")
        val value = initialData.firstElementOfC7[idx, stack]

        return value.cell(stack)
            ?: error("Unexpected $idx parameter value: $value")
    }

    fun resolveResultStackImpl(methodResult: TvmResult): TvmTestResult =
        when (methodResult) {
            TvmResult.NoCall -> {
                error("Unexpected result when resolving: $methodResult")
            }

            is TvmResult.TvmFailure -> {
                var node = methodResult.pathNodeAtFailurePoint
                while (node.statement is TvmArtificialInst) {
                    node = node.parent
                        ?: error("Unexpected execution path without non-artificial instructions")
                }

                TvmTestFailure(methodResult, node.statement, methodResult.exit.exitCode)
            }

            is TvmResult.TvmComputePhaseSuccess -> {
                val results = methodResult.stack.results
                val resolvedResults = results.filterNotNull().map { resolveStackValue(it) }
                TvmSuccessfulExecution(methodResult.exit.exitCode, resolvedResults)
            }

            is TvmStructuralError -> {
                resolveTvmStructuralError(state.lastStmt, methodResult)
            }

            is TvmResult.TvmSoftFailure -> {
                TvmExecutionWithSoftFailure(state.lastStmt, methodResult)
            }

            is TvmResult.TvmActionPhaseSuccess -> {
                TvmSuccessfulActionPhase
            }
        }

    fun resolveResultStack(): TvmTestResult {
        val methodResult = state.result
        return resolveResultStackImpl(methodResult)
    }

    private fun resolveOutMessage(message: MessageAsStackArguments): TvmTestMessage {
        val mode =
            when (message.source) {
                is MessageSource.SentWithMode -> message.source.mode
                is MessageSource.Bounced -> null
            }

        return TvmTestMessage(
            value = resolveInt257(message.msgValue),
            fullMessage = resolveCell(message.fullMsgCell),
            bodySlice = resolveSlice(message.msgBodySlice),
            mode = mode?.let { resolveInt257(it).value },
        )
    }

    fun resolveReceivedMessage(message: ReceivedMessage): TvmTestInput.ReceivedTestMessage =
        when (message) {
            is ReceivedMessage.InputMessage -> {
                TvmTestInput.ReceivedTestMessage.InputMessage(
                    when (val it = message.input) {
                        is RecvExternalInput -> resolveRecvExternalInput(it)
                        is RecvInternalInput -> resolveRecvInternalInput(it)
                    },
                )
            }

            is ReceivedMessage.MessageFromOtherContract -> {
                TvmTestInput.ReceivedTestMessage.MessageFromOtherContract(
                    message.sender,
                    message.receiver,
                    resolveOutMessage(message.message),
                )
            }
        }

    fun resolveOutMessages(): List<Pair<ContractId, TvmTestMessage?>> =
        state.unprocessedMessages.map { (contractId, message) ->
            // workaround to support the resolving of the message even in failed states
            val oldIsExceptional = state.isExceptional
            state.isExceptional = false

            val scope =
                TvmStepScopeManager(state, UForkBlackList.Companion.createDefault(), allowFailuresOnCurrentStep = false)
            val messageStackArgs = message.toStackArgs()
            val result = contractId to resolveOutMessage(messageStackArgs)

            val stepResult = scope.stepResult()
            check(stepResult.originalStateAlive) {
                "Original state died while building full message"
            }
            check(stepResult.forkedStates.none()) {
                "Unexpected forks while building full message"
            }
            state.isExceptional = oldIsExceptional
            result
        }

    fun resolveAdditionalInputs(): Map<Int, TvmTestInput> =
        state.additionalInputs.entries.associate { (inputId, symbolicInput) ->
            val resolvedInput =
                when (symbolicInput) {
                    is RecvExternalInput -> resolveRecvExternalInput(symbolicInput)
                    is RecvInternalInput -> resolveRecvInternalInput(symbolicInput)
                }
            inputId to resolvedInput
        }

    private fun resolveTvmStructuralError(
        lastStmt: TvmInst,
        exit: TvmStructuralError,
    ): TvmExecutionWithStructuralError {
        val resolvedExit =
            when (val structuralExit = exit.exit) {
                is TvmUnexpectedDataReading -> {
                    TvmUnexpectedDataReading(
                        resolveCellDataType(structuralExit.readingType),
                    )
                }

                is TvmReadingOfUnexpectedType -> {
                    TvmReadingOfUnexpectedType(
                        expectedLabel = resolveBuiltinLabel(structuralExit.expectedLabel, structuralExit.typeArgs),
                        typeArgs = emptyList(),
                        actualType = resolveCellDataType(structuralExit.actualType),
                    )
                }

                is TvmUnexpectedEndOfReading -> {
                    TvmUnexpectedEndOfReading
                }

                is TvmUnexpectedRefReading -> {
                    TvmUnexpectedRefReading
                }

                is TvmReadingOutOfSwitchBounds -> {
                    TvmReadingOutOfSwitchBounds(
                        resolveCellDataType(structuralExit.readingType),
                    )
                }

                is TvmReadingSwitchWithUnexpectedType -> {
                    TvmReadingSwitchWithUnexpectedType(
                        resolveCellDataType(structuralExit.readingType),
                    )
                }
            }
        return TvmExecutionWithStructuralError(lastStmt, resolvedExit)
    }

    private fun resolveBuiltinLabel(
        label: TlbBuiltinLabel,
        args: List<UExpr<TvmSizeSort>>,
    ) = when (label) {
        is TlbIntegerLabel -> {
            val concreteSize = resolveInt(label.bitSize(ctx, args).sizeBits)
            TlbIntegerLabelOfConcreteSize(concreteSize, label.isSigned, label.endian)
        }

        is TlbResolvedBuiltinLabel -> {
            label
        }

        is TlbBitArrayByRef -> {
            val concreteSize = resolveInt(label.sizeBits)
            TlbBitArrayOfConcreteSize(concreteSize)
        }
    }

    fun resolveGasUsage(): Int = model.eval(state.calcConsumedGas()).intValue()

    fun resolvePhaseGasUsage(
        eventBegin: Int,
        eventEnd: Int,
    ): Int = model.eval(state.calcPhaseConsumedGas(eventBegin, eventEnd)).intValue()

    private fun resolveStackValue(stackValue: TvmStackValue): TvmTestValue =
        when (stackValue) {
            is TvmStack.TvmStackIntValue -> {
                resolveInt257(stackValue.intValue)
            }

            is TvmStack.TvmStackCellValue -> {
                resolveCell(
                    stackValue.cellValue.also { state.ensureSymbolicCellInitialized(it) },
                )
            }

            is TvmStack.TvmStackSliceValue -> {
                resolveSlice(
                    stackValue.sliceValue.also { state.ensureSymbolicSliceInitialized(it) },
                )
            }

            is TvmStack.TvmStackBuilderValue -> {
                resolveBuilder(
                    stackValue.builderValue.also {
                        state.ensureSymbolicBuilderInitialized(it)
                    },
                )
            }

            is TvmStack.TvmStackNullValue -> {
                TvmTestNullValue
            }

            is TvmStack.TvmStackContinuationValue -> {
                TODO()
            }

            is TvmStackTupleValue -> {
                resolveTuple(stackValue)
            }
        }

    fun resolveRef(ref: UHeapRef): TvmTestReferenceValue {
        val concreteRef = evaluateInModel(ref) as UConcreteHeapRef
        val possibleTypes = state.getPossibleTypes(concreteRef)
        val type = possibleTypes.first()
        require(type is TvmFinalReferenceType)
        return when (type) {
            TvmSliceType -> resolveSlice(ref)
            TvmDataCellType, TvmDictCellType -> resolveCell(ref)
            TvmBuilderType -> resolveBuilder(ref)
        }
    }

    private fun <T : USort> evaluateInModel(expr: UExpr<T>): UExpr<T> = model.eval(expr)

    private fun resolveTuple(tuple: TvmStackTupleValue): TvmTestTupleValue =
        when (tuple) {
            is TvmStackTupleValueConcreteNew -> {
                val elements =
                    tuple.entries.map {
                        it.cell(stack)?.let { value -> resolveStackValue(value) }
                            ?: TvmTestNullValue // We do not care what is its real value as it was never used
                    }

                TvmTestTupleValue(elements)
            }

            is TvmStack.TvmStackTupleValueInputValue -> {
                val size = resolveInt(tuple.size)
                val elements =
                    (0..<size).map {
                        tuple[it, stack].cell(stack)?.let { value -> resolveStackValue(value) }
                            ?: TvmTestNullValue // We do not care what is its real value as it was never used
                    }

                TvmTestTupleValue(elements)
            }
        }

    private fun resolveBuilder(builder: UHeapRef): TvmTestBuilderValue {
        val ref = evaluateInModel(builder) as UConcreteHeapRef

        val cached = resolvedCache[ref.address]
        check(cached is TvmTestDataCellValue?)
        if (cached != null) {
            return TvmTestBuilderValue(cached.data, cached.refs)
        }

        val cell = resolveDataCell(ref, builder)
        return TvmTestBuilderValue(cell.data, cell.refs)
    }

    private fun resolveSlice(slice: UHeapRef): TvmTestSliceValue =
        with(ctx) {
            val cellValue = resolveCell(memory.readField(slice, TvmContext.sliceCellField, addressSort))
            require(cellValue is TvmTestDataCellValue)
            val dataPosValue = resolveInt(state.fieldManagers.cellDataLengthFieldManager.readSliceDataPos(state, slice))
            val refPosValue = resolveInt(memory.readField(slice, TvmContext.sliceRefPosField, sizeSort))

            TvmTestSliceValue(cellValue, dataPosValue, refPosValue)
        }

    private fun resolveDataCell(
        modelRef: UConcreteHeapRef,
        cell: UHeapRef,
    ): TvmTestDataCellValue =
        with(ctx) {
            if (modelRef.address == NULL_ADDRESS) {
                return@with TvmTestDataCellValue()
            }

            // cell is not in path constraints => just return empty cell
            if (modelRef.isStatic && modelRef !in constraintVisitor.refs) {
                return@with TvmTestDataCellValue()
            }

            val data = resolveCellData(cell)

            val refsLength =
                resolveInt(state.fieldManagers.cellRefsLengthFieldManager.readCellRefLength(state, cell)).also {
                    check(it in 0..4) {
                        "Unexpected cell ref length: $it"
                    }
                }
            val refs = mutableListOf<TvmTestCellValue>()

            val storedRefs = mutableMapOf<Int, TvmTestCellValue>()
            val updateNode = memory.tvmCellRefsRegion().getRefsUpdateNode(modelRef)

            resolveRefUpdates(updateNode, storedRefs, refsLength)

            for (idx in 0 until refsLength) {
                val refCell =
                    storedRefs[idx]
                        ?: TvmTestDataCellValue()

                refs.add(refCell)
            }

            val knownActions = state.dataCellLoadedTypeInfo.referenceToActions[modelRef] ?: persistentListOf()
            val tvmCellValue = TvmTestDataCellValue(data, refs, resolveTypeLoad(knownActions))

            tvmCellValue.also { resolvedCache[modelRef.address] = tvmCellValue }
        }

    private fun resolveDictCell(modelRef: UConcreteHeapRef): TvmTestDictCellValue =
        with(ctx) {
            if (modelRef.address == NULL_ADDRESS) {
                error("Unexpected dict ref: $modelRef")
            }

            val keyLength = resolveInt(memory.readField(modelRef, dictKeyLengthField, sizeSort))
            val dictId = DictId(keyLength)
            val keySort = mkBvSort(keyLength.toUInt())
            val keySetEntries = dictKeyEntries(model, memory, modelRef, dictId, keySort)

            val keySet = mutableSetOf<UExpr<UBvSort>>()
            val resultEntries = mutableMapOf<TvmTestIntegerValue, TvmTestSliceValue>()

            val inputDict = state.inputDictionaryStorage.memory[modelRef]
            if (inputDict != null) {
                return handleInputDict(inputDict, modelRef, dictId, keyLength, modelRef)
            }
            for (entry in keySetEntries) {
                val key = entry.setElement
                val keyContains = state.allocatedDictContainsKey(modelRef, dictId, key)
                if (evaluateInModel(keyContains).isTrue) {
                    val evaluatedKey = evaluateInModel(key)
                    if (!keySet.add(evaluatedKey)) {
                        continue
                    }

                    val resolvedKey = TvmTestIntegerValue(extractInt257(evaluatedKey))

                    val value = state.dictGetValue(modelRef, dictId, evaluatedKey)
                    val resolvedValue = resolveSlice(value)

                    resultEntries[resolvedKey] = resolvedValue
                }
            }

            return TvmTestDictCellValue(keyLength, resultEntries).also { resolvedCache[modelRef.address] = it }
        }

    private fun handleInputDict(
        inputDict: InputDict,
        dict: UConcreteHeapRef,
        dictId: DictId,
        keyLength: Int,
        modelRef: UConcreteHeapRef,
    ): TvmTestDictCellValue {
        val rootInputDictInfo =
            state.inputDictionaryStorage.rootInformation[inputDict.rootInputDictId]
                ?: error("no root input dict")
        val resultEntries =
            inputDict
                .getCurrentlyDiscoveredKeys(ctx, rootInputDictInfo)
                .mapNotNull { (key, condition) ->
                    if (evaluateInModel(condition).isTrue) {
                        val evaluatedKey = evaluateInModel(key.expr)
                        val resolvedKey = TvmTestIntegerValue(extractInt257(evaluatedKey))
                        val value = state.dictGetValue(dict, dictId, evaluatedKey)
                        val resolvedValue = resolveSlice(value)
                        resolvedKey to resolvedValue
                    } else {
                        null
                    }
                }
        val resultEntriesNoRepeat = resultEntries.toSet().toMap()
        return TvmTestDictCellValue(
            keyLength,
            resultEntriesNoRepeat,
        ).also { resolvedCache[modelRef.address] = it }
    }

    private fun buildDefaultCell(cellInfo: TvmParameterInfo.CellInfo): TvmTestCellValue =
        when (cellInfo) {
            is TvmParameterInfo.UnknownCellInfo -> {
                TvmTestDataCellValue()
            }

            is TvmParameterInfo.DictCellInfo -> {
                getDefaultDict(cellInfo.keySize)
            }

            is TvmParameterInfo.DataCellInfo -> {
                val label = cellInfo.dataCellStructure
                val defaultValue =
                    state.dataCellInfoStorage.mapper.calculatedTlbLabelInfo
                        .getDefaultCell(label)
                check(defaultValue != null) {
                    "Default cell for label ${label.name} must be calculated"
                }
                defaultValue
            }
        }

    private fun resolveCell(cell: UHeapRef): TvmTestCellValue =
        with(ctx) {
            val modelRef = evaluateInModel(cell) as UConcreteHeapRef
            if (modelRef.address == NULL_ADDRESS) {
                return@with TvmTestDataCellValue()
            }

            val cached = resolvedCache[modelRef.address]
            if (cached != null) return cached

            // This is a special situation for a case when a child of some cell with TL-B scheme
            // was requested for the first time only during test resolving process.
            // Since structural constraints are generated lazily, they were not generated for
            // this child yet. To avoid generation of a test that violates TL-B scheme
            // we provide [TvmTestCellValue] with default contents for the scheme.
            if (!labelMapper.proactiveStructuralConstraintsWereCalculated(modelRef) &&
                labelMapper.addressWasGiven(modelRef)
            ) {
                return buildDefaultCell(labelMapper.getLabelFromModel(model, modelRef))
            }

            val typeVariants = state.getPossibleTypes(modelRef)

            // If typeVariants has more than one type, we can choose any of them.
            val type = typeVariants.first()

            require(type is TvmDictCellType || type is TvmDataCellType) {
                "Unexpected type: $type"
            }

            if (type is TvmDictCellType) {
                return resolveDictCell(modelRef)
            }

            resolveDataCell(modelRef, cell)
        }

    private fun resolveRefUpdates(
        updateNode: TvmRefsMemoryRegion.TvmRefsRegionUpdateNode<TvmSizeSort, UAddressSort>?,
        storedRefs: MutableMap<Int, TvmTestCellValue>,
        refsLength: Int,
    ) {
        @Suppress("NAME_SHADOWING")
        var updateNode = updateNode

        while (updateNode != null) {
            when (updateNode) {
                is TvmRefsMemoryRegion.TvmRefsRegionInputNode -> {
                    val idx = resolveInt(updateNode.key)
                    // [idx] might be >= [refsLength]
                    // because we read refs when generating structural constraints
                    // without checking actual number of refs in a cell
                    if (idx < refsLength) {
                        val value = TvmCellRefsRegionValueInfo(state).actualizeSymbolicValue(updateNode.value)
                        val refCell = resolveCell(value)
                        storedRefs.putIfAbsent(idx, refCell)
                    }
                }

                is TvmRefsMemoryRegion.TvmRefsRegionEmptyUpdateNode -> {}

                is TvmRefsMemoryRegion.TvmRefsRegionCopyUpdateNode -> {
                    val guardValue = evaluateInModel(updateNode.guard)
                    if (guardValue.isTrue) {
                        resolveRefUpdates(updateNode.updates, storedRefs, refsLength)
                    }
                }

                is TvmRefsMemoryRegion.TvmRefsRegionPinpointUpdateNode -> {
                    val guardValue = evaluateInModel(updateNode.guard)
                    if (guardValue.isTrue) {
                        updateNode.values.forEach { (key, value) ->
                            val idx = resolveInt(key)
                            if (idx < refsLength) {
                                val refCell = resolveCell(value)
                                storedRefs.putIfAbsent(idx, refCell)
                            }
                        }
                    }
                }
            }

            updateNode = updateNode.prevUpdate
        }
    }

    private fun resolveTypeLoad(loads: List<TvmDataCellLoadedTypeInfo.Action>): List<TvmCellDataTypeLoad> {
        val resolved =
            loads.mapNotNull {
                if (it is TvmDataCellLoadedTypeInfo.LoadData<*> && model.eval(it.guard).isTrue) {
                    TvmCellDataTypeLoad(resolveCellDataType(it.type), resolveInt(it.offset))
                } else {
                    null
                }
            }
        // remove duplicates (they might appear if we traverse the cell twice or more)
        return resolved.toSet().sortedBy { it.offset }
    }

    private fun resolveCellDataType(type: TvmCellDataTypeRead<*>): TvmTestCellDataTypeRead =
        when (type) {
            is TvmCellDataIntegerRead -> {
                TvmTestCellDataIntegerRead(
                    resolveInt(type.sizeBits),
                    type.isSigned,
                    type.endian,
                )
            }

            is TvmCellMaybeConstructorBitRead -> {
                TvmTestCellDataMaybeConstructorBitRead
            }

            is TvmCellDataBitArrayRead -> {
                TvmTestCellDataBitArrayRead(resolveInt(type.sizeBits))
            }

            is TvmCellDataMsgAddrRead -> {
                TvmTestCellDataMsgAddrRead
            }

            is TvmCellDataCoinsRead -> {
                TvmTestCellDataCoinsRead
            }
        }

    fun resolveInt257(expr: UExpr<out USort>): TvmTestIntegerValue {
        val value = extractInt257(evaluateInModel(expr))
        return TvmTestIntegerValue(value)
    }

    private fun resolveCellData(cell: UHeapRef): String {
        val modelRef = model.eval(cell) as UConcreteHeapRef

        if (labelMapper.addressWasGiven(modelRef)) {
            val label = labelMapper.getLabelFromModel(model, modelRef)
            if (label is TvmParameterInfo.DataCellInfo) {
                val valueFromTlbFields =
                    readInModelFromTlbFields(cell, this@TvmTestStateResolver, label.dataCellStructure)

                val dataLength =
                    resolveInt(state.fieldManagers.cellDataLengthFieldManager.readCellDataLength(state, cell)).also {
                        check(it in 0..MAX_DATA_LENGTH) {
                            "Unexpected data length"
                        }
                    }

                check(valueFromTlbFields.length == dataLength) {
                    "Inconsistent data from TL-B field"
                }

                if (performAdditionalChecks &&
                    modelRef.address in state.fieldManagers.cellDataFieldManager.getCellsWithAssertedCellData()
                ) {
                    val symbolicData =
                        state.fieldManagers.cellDataFieldManager.readCellDataWithoutAsserts(
                            state,
                            cell,
                        )
                    val data = extractCellData(evaluateInModel(symbolicData))
                    val dataFromField = data.take(dataLength)

                    check(dataFromField == valueFromTlbFields) {
                        "Data from cellDataField and tlb fields for ref $modelRef are inconsistent\n" +
                            "cellDataField: $dataFromField\n" +
                            "   tlb fields: $valueFromTlbFields"
                    }
                }

                return valueFromTlbFields
            }
        }

        val symbolicData = state.fieldManagers.cellDataFieldManager.readCellDataWithoutAsserts(state, cell)
        val data = extractCellData(evaluateInModel(symbolicData))
        val dataLength =
            resolveInt(state.fieldManagers.cellDataLengthFieldManager.readCellDataLength(state, cell)).also {
                check(it in 0..MAX_DATA_LENGTH) {
                    "Unexpected data length"
                }
            }

        return data.take(dataLength)
    }

    private fun resolveInt(expr: UExpr<out USort>): Int = extractInt(evaluateInModel(expr))

    private fun extractInt(expr: UExpr<out USort>): Int =
        (expr as? KBitVecValue)?.toBigIntegerSigned()?.toInt() ?: error("Unexpected expr $expr")

    private fun extractCellData(expr: UExpr<out USort>): String =
        (expr as? KBitVecValue)?.stringValue ?: error("Unexpected expr $expr")

    private fun extractInt257(expr: UExpr<out USort>): BigInteger =
        (expr as? KBitVecValue)?.toBigIntegerSigned() ?: error("Unexpected expr $expr")
}

private class ConstraintsVisitor(
    ctx: TvmContext,
) : UExprTranslator<TvmType, TvmSizeSort>(ctx) {
    val refs = mutableSetOf<UConcreteHeapRef>()

    override fun transform(expr: UConcreteHeapRef): UHeapRef {
        refs.add(expr)
        return super.transform(expr)
    }
}
