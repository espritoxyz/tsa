package org.usvm.machine.state

import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.ton.bytecode.BALANCE_PARAMETER_IDX
import org.ton.bytecode.INBOUND_MESSAGE_VALUE_PARAMETER_IDX
import org.ton.bytecode.TsaContractCode
import org.ton.bytecode.TvmCell
import org.ton.bytecode.TvmCellData
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.writeField
import org.usvm.machine.Int257Expr
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.CONFIG_KEY_LENGTH
import org.usvm.machine.TvmContext.Companion.INT_BITS
import org.usvm.machine.TvmContext.Companion.dictKeyLengthField
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmStack.TvmStackCellValue
import org.usvm.machine.state.TvmStack.TvmStackEntry
import org.usvm.machine.state.TvmStack.TvmStackIntValue
import org.usvm.machine.state.TvmStack.TvmStackNullValue
import org.usvm.machine.state.TvmStack.TvmStackTupleValue
import org.usvm.machine.state.TvmStack.TvmStackTupleValueConcreteNew
import org.usvm.machine.state.TvmStack.TvmStackValue
import org.usvm.machine.types.TvmDictCellType
import org.usvm.mkSizeExpr
import org.usvm.sizeSort

fun TvmState.getContractInfoParam(idx: Int): TvmStackValue {
    require(idx in 0..17) {
        "Unexpected param index $idx"
    }

    return getContractInfo()[idx, stack].cell(stack)
        ?: error("Unexpected param value")
}

fun TvmState.getContractInfoParamOf(
    idx: Int,
    contractId: ContractId,
): TvmStackValue {
    require(idx in 0..17) {
        "Unexpected param index $idx"
    }

    return getContractInfoOf(contractId)[idx, stack].cell(stack)
        ?: error("Unexpected param value")
}

fun TvmStepScopeManager.getCellContractInfoParam(idx: Int): UHeapRef? {
    val cell = calcOnState { getContractInfoParam(idx).cellValue }

    if (cell == null) {
        doWithStateCtx {
            ctx.throwTypeCheckError(this)
        }
    }

    return cell
}

fun TvmStepScopeManager.getIntContractInfoParam(idx: Int): UExpr<TvmInt257Sort>? {
    val cell = calcOnState { getContractInfoParam(idx).intValue }

    if (cell == null) {
        doWithStateCtx {
            ctx.throwTypeCheckError(this)
        }
    }

    return cell
}

fun TvmState.getBalance(): UExpr<TvmInt257Sort>? =
    getContractInfoParam(BALANCE_PARAMETER_IDX)
        .tupleValue
        ?.get(0, stack)
        ?.cell(stack)
        ?.intValue

fun TvmState.getBalanceOf(contractId: ContractId): UExpr<TvmInt257Sort>? =
    getContractInfoParamOf(BALANCE_PARAMETER_IDX, contractId)
        .tupleValue
        ?.get(0, stack)
        ?.cell(stack)
        ?.intValue

fun TvmState.setBalance(newBalance: UExpr<TvmInt257Sort>) {
    val old =
        getContractInfoParam(BALANCE_PARAMETER_IDX).tupleValue ?: error("Unexpected param value")
    val new =
        old.set(0, TvmStack.TvmConcreteStackEntry(TvmStackIntValue(newBalance)))
    setContractInfoParam(BALANCE_PARAMETER_IDX, TvmStack.TvmConcreteStackEntry(new))
}

fun TvmState.setContractInfoParam(
    idx: Int,
    value: TvmStackEntry,
) {
    require(idx in 0..14) {
        "Unexpected param index $idx"
    }

    val updatedContractInfo = getContractInfo().set(idx, value)
    val registers = registersOfCurrentContract
    val updatedC7 = registers.c7.value.set(0, updatedContractInfo.toStackEntry())

    registers.c7 = C7Register(updatedC7)
}

fun TvmStepScopeManager.getConfigParam(idx: UExpr<TvmInt257Sort>): UHeapRef? {
    val configDict = calcOnState { getConfig() }
    val sliceValue =
        calcOnStateCtx {
            dictGetValue(
                configDict,
                DictId(CONFIG_KEY_LENGTH),
                idx.extractToSort(mkBvSort(CONFIG_KEY_LENGTH.toUInt())),
            )
        }

    return slicePreloadNextRef(sliceValue)
}

fun TvmState.configContainsParam(idx: UExpr<TvmInt257Sort>): UBoolExpr =
    with(ctx) {
        val configDict = getConfig()

        allocatedDictContainsKey(
            configDict,
            DictId(CONFIG_KEY_LENGTH),
            idx.extractToSort(mkBvSort(CONFIG_KEY_LENGTH.toUInt())),
        )
    }

fun TvmState.getGlobalVariable(
    idx: Int,
    stack: TvmStack,
): TvmStackValue {
    require(idx in 0..<255) {
        "Unexpected global variable with index $idx"
    }
    val registers = registersOfCurrentContract
    val globalEntries =
        registers.c7.value.entries
            .extendToSize(idx + 1)

    return globalEntries.getOrNull(idx)?.cell(stack)
        ?: error("Cannot find global variable with index $idx")
}

fun TvmState.setGlobalVariable(
    idx: Int,
    value: TvmStackEntry,
) {
    require(idx in 0..<255) {
        "Unexpected setting global variable with index $idx"
    }

    val registers = registersOfCurrentContract
    val updatedC7 =
        TvmStackTupleValueConcreteNew(
            ctx,
            registers.c7.value.entries
                .extendToSize(idx + 1),
        ).set(idx, value)

    registers.c7 = C7Register(updatedC7)
}

fun TvmState.initC7(contractInfo: TvmStackTupleValue): TvmStackTupleValueConcreteNew =
    TvmStackTupleValueConcreteNew(
        ctx,
        persistentListOf(contractInfo.toStackEntry()),
    )

fun TvmState.getInboundMessageValue(): Int257Expr? = getContractInfoParam(INBOUND_MESSAGE_VALUE_PARAMETER_IDX).intValue

val BIT_PRICE_PS: Long get() = TvmConfigBoc.storagePrices.bitPricePs
val CELL_PRICE_PS: Long get() = TvmConfigBoc.storagePrices.cellPricePs
val MC_BIT_PRICE_PS: Long get() = TvmConfigBoc.storagePrices.mcBitPricePs
val MC_CELL_PRICE_PS: Long get() = TvmConfigBoc.storagePrices.mcCellPricePs

val LUMP_PRICE_MASTERCHAIN: Long get() = TvmConfigBoc.masterchainMsgPrices.lumpPrice
val FIRST_FRAC_MASTERCHAIN: Long get() = TvmConfigBoc.masterchainMsgPrices.firstFrac
val BIT_PRICE_MASTERCHAIN: Long get() = TvmConfigBoc.masterchainMsgPrices.bitPrice
val CELL_PRICE_MASTERCHAIN: Long get() = TvmConfigBoc.masterchainMsgPrices.cellPrice
val FLAT_GAS_LIMIT_MASTERCHAIN: Long get() = TvmConfigBoc.masterchainGasPrices.flatGasLimit
val FLAT_GAS_PRICE_MASTERCHAIN: Long get() = TvmConfigBoc.masterchainGasPrices.flatGasPrice
val GAS_PRICE_MASTERCHAIN: Long get() = TvmConfigBoc.masterchainGasPrices.gasPrice

val LUMP_PRICE: Long get() = TvmConfigBoc.basechainMsgPrices.lumpPrice
val FIRST_FRAC: Long get() = TvmConfigBoc.basechainMsgPrices.firstFrac
val BIT_PRICE: Long get() = TvmConfigBoc.basechainMsgPrices.bitPrice
val CELL_PRICE: Long get() = TvmConfigBoc.basechainMsgPrices.cellPrice
val FLAT_GAS_LIMIT: Long get() = TvmConfigBoc.basechainGasPrices.flatGasLimit
val FLAT_GAS_PRICE: Long get() = TvmConfigBoc.basechainGasPrices.flatGasPrice
val GAS_PRICE: Long get() = TvmConfigBoc.basechainGasPrices.gasPrice

fun makeBalanceEntry(
    ctx: TvmContext,
    balance: UExpr<TvmInt257Sort>,
): TvmStackTupleValueConcreteNew =
    TvmStackTupleValueConcreteNew(
        ctx,
        persistentListOf(
            TvmStackIntValue(balance).toStackEntry(),
            TvmStackNullValue.toStackEntry(),
        ),
    )

fun TvmState.initContractInfo(
    contractCode: TsaContractCode,
    concreteData: TvmConcreteContractData,
): TvmStackTupleValueConcreteNew =
    with(ctx) {
        val tag = TvmStackIntValue(mkBvHex("076ef1ea", sizeBits = INT_BITS).uncheckedCast())

        // Right now, this parameter can only be set to zero in emulator
        // https://github.com/ton-blockchain/ton/blob/59a8cf0ae5c3062d14ec4c89a04fee80b5fd05c1/crypto/smc-envelope/SmartContract.cpp#L153
        val actions = TvmStackIntValue(zeroValue)

        // Right now, this parameter can only be set to zero in emulator
        // https://github.com/ton-blockchain/ton/blob/59a8cf0ae5c3062d14ec4c89a04fee80b5fd05c1/crypto/smc-envelope/SmartContract.cpp#L154
        val msgsSent = TvmStackIntValue(zeroValue)

        val unixTime = TvmStackIntValue(time)

        // Right now, this parameter can only be set to zero in emulator
        // https://github.com/ton-blockchain/ton/blob/59a8cf0ae5c3062d14ec4c89a04fee80b5fd05c1/crypto/smc-envelope/SmartContract.cpp#L156
        val blockLogicTime = TvmStackIntValue(zeroValue)

        // Right now, this parameter can only be set to zero in emulator
        // https://github.com/ton-blockchain/ton/blob/59a8cf0ae5c3062d14ec4c89a04fee80b5fd05c1/crypto/smc-envelope/SmartContract.cpp#L157
        val transactionLogicTime = TvmStackIntValue(zeroValue)

        val randomSeed = TvmStackIntValue(makeSymbolicPrimitive(int257sort))

        val initialBalance =
            if (concreteData.initialBalance == null) {
                makeSymbolicPrimitive(mkBvSort(TvmContext.BITS_FOR_BALANCE), TvmBalance()).zeroExtendToSort(int257sort)
            } else {
                concreteData.initialBalance.toBv257()
            }

        val balance = makeBalanceEntry(ctx, initialBalance)

        val (addrValue, workchain) =
            if (concreteData.addressBits == null) {
                generateSymbolicAddressCell(TvmContractAddress())
            } else {
                val address = allocateCell(TvmCell(data = TvmCellData(concreteData.addressBits), refs = emptyList()))
                val workchain =
                    mkBv(
                        concreteData.addressBits.substring(
                            TvmContext.ADDRESS_TAG_LENGTH + 1,
                            TvmContext.ADDRESS_TAG_LENGTH + 1 + TvmContext.STD_WORKCHAIN_BITS,
                        ),
                        TvmContext.STD_WORKCHAIN_BITS.toUInt(),
                    )
                address to workchain
            }

        val extendedWorkchain = workchain.signedExtendToInteger()

        val addr = TvmStackCellValue(addrValue)
        val config = TvmStackCellValue(initConfigRoot())
        val code = TvmStackCellValue(allocateCell(contractCode.codeCell))

        // TODO support `incomingValue` param
        val incomingValue = TvmStackNullValue

        // Right now, this parameter can only be set to zero in emulator
        // https://github.com/ton-blockchain/ton/blob/59a8cf0ae5c3062d14ec4c89a04fee80b5fd05c1/crypto/smc-envelope/SmartContract.cpp#L166
        val storagePhaseFees = TvmStackIntValue(zeroValue)

        // TODO support `prevBlocksInfo` param
        val prevBlocksInfo = TvmStackNullValue

        // TODO support `unpacked_config_tuple` param
        val unpackedConfigTuple = TvmStackNullValue

        // Right now, this parameter can only be set to zero in emulator
        // https://github.com/ton-blockchain/ton/blob/59a8cf0ae5c3062d14ec4c89a04fee80b5fd05c1/crypto/smc-envelope/SmartContract.cpp#L176
        val duePayment = TvmStackIntValue(zeroValue)

        // TODO support `precompiled` param
        val gasUsageIfPrecompiled = TvmStackIntValue(zeroValue)

        // We can add constraints manually to path constraints because model list is empty
        check(models.isEmpty()) {
            "Model list must be empty at this point but is not."
        }
        pathConstraints += mkBvSignedGreaterOrEqualExpr(blockLogicTime.intValue, zeroValue)
        pathConstraints += mkBvSignedGreaterExpr(maxLogicalTimeValue, blockLogicTime.intValue)
        pathConstraints += mkBvSignedGreaterOrEqualExpr(transactionLogicTime.intValue, zeroValue)
        pathConstraints += mkBvSignedGreaterExpr(maxLogicalTimeValue, transactionLogicTime.intValue)
        pathConstraints += mkBvSignedGreaterOrEqualExpr(initialBalance, zeroValue)
        pathConstraints += mkBvSignedGreaterOrEqualExpr(storagePhaseFees.intValue, zeroValue)
        pathConstraints += mkAnd((extendedWorkchain eq masterchain) or (extendedWorkchain eq baseChain))

        val paramList =
            listOf(
                tag,
                actions,
                msgsSent,
                unixTime,
                blockLogicTime,
                transactionLogicTime,
                randomSeed,
                balance,
                addr,
                config,
                code,
                incomingValue,
                storagePhaseFees,
                prevBlocksInfo,
                unpackedConfigTuple,
                duePayment,
                gasUsageIfPrecompiled,
                TvmStackNullValue, // INMSGPARAMS (will be initialized later)
            )

        TvmStackTupleValueConcreteNew(
            ctx,
            paramList.map { it.toStackEntry() }.toPersistentList(),
        )
    }

private fun TvmState.initConfigRoot(): UHeapRef =
    with(ctx) {
        val configDict = allocDict(keyLength = CONFIG_KEY_LENGTH)
        for ((key, cellValue) in TvmConfigBoc.entries) {
            val cellRef = allocateCell(cellValue)
            addDictEntry(configDict, key, cellRef)
        }
        configDict
    }

private fun TvmState.allocDict(keyLength: Int): UConcreteHeapRef =
    with(ctx) {
        memory.allocConcrete(TvmDictCellType).also {
            memory.writeField(it, dictKeyLengthField, sizeSort, mkSizeExpr(keyLength), guard = trueExpr)
        }
    }

private fun TvmState.addDictEntry(
    dict: UHeapRef,
    key: Int,
    value: UHeapRef,
) = with(ctx) {
    val builder = allocEmptyCell().also { builderStoreNextRefNoOverflowCheck(it, value) }
    val sliceValue = allocSliceFromCell(builder)
    dictAddKeyValue(
        dict,
        DictId(CONFIG_KEY_LENGTH),
        mkBv(key, CONFIG_KEY_LENGTH.toUInt()),
        sliceValue,
    )
}

private fun TvmState.getContractInfo() =
    registersOfCurrentContract.c7.value[0, stack]
        .cell(stack)
        ?.tupleValue
        ?: error("Unexpected contract info value")

private fun TvmState.getContractInfoOf(contractId: ContractId) =
    contractIdToFirstElementOfC7[contractId]?.tupleValue
        ?: error("Unexpected contract info value")

private fun TvmState.getConfig() =
    getContractInfo()[9, stack].cell(stack)?.cellValue
        ?: error("Unexpected config value")

private fun PersistentList<TvmStackEntry>.extendToSize(newSize: Int): PersistentList<TvmStackEntry> {
    if (size >= newSize) {
        return this
    }

    val newValuesSize = newSize - size
    val newValues = List(newValuesSize) { TvmStackNullValue.toStackEntry() }

    return addAll(newValues)
}
