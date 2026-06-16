package org.usvm.machine.state

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBvSort
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
import org.usvm.machine.TvmConcreteGeneralData
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
import org.usvm.machine.state.TvmStack.TvmStackSliceValue
import org.usvm.machine.state.TvmStack.TvmStackTupleValue
import org.usvm.machine.state.TvmStack.TvmStackTupleValueConcreteNew
import org.usvm.machine.state.TvmStack.TvmStackValue
import org.usvm.machine.types.TvmDictCellType
import org.usvm.mkSizeExpr
import org.usvm.sizeSort
import java.math.BigInteger

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

fun TvmState.getInboundMessageValue(): Int257Expr? =
    getContractInfoParam(INBOUND_MESSAGE_VALUE_PARAMETER_IDX)
        .tupleValue
        ?.get(0, stack)
        ?.cell(stack)
        ?.intValue

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

/**
 * Builds incoming message value tuple for c7[11]: (Integer balance, Maybe Cell extra-currencies).
 * See https://docs.ton.org/tvm/registers#c7---environment-information-and-global-variables
 */
fun makeIncomingValueEntry(
    ctx: TvmContext,
    msgValue: UExpr<TvmInt257Sort>,
): TvmStackTupleValueConcreteNew =
    TvmStackTupleValueConcreteNew(
        ctx,
        persistentListOf(
            TvmStackIntValue(msgValue).toStackEntry(),
            TvmStackNullValue.toStackEntry(),
        ),
    )

/**
 * Builds the unpacked config tuple for c7[14] (UNPACKEDCONFIGTUPLE).
 *
 * Mirrors TON's `get_unpacked_config_tuple`: a tuple of slices over global config params, with a
 * null entry for any param that is absent. The first element is the current storage prices record
 * (the latest entry of config param 18); the rest are slices over config params 19, 20, 21, 24, 25, 43.
 * See https://github.com/ton-blockchain/ton/blob/master/crypto/block/mc-config.cpp
 */
fun TvmState.makeUnpackedConfigTuple(): TvmStackTupleValueConcreteNew {
    fun sliceEntryOf(cell: TvmCell?): TvmStackValue =
        if (cell == null) {
            TvmStackNullValue
        } else {
            TvmStackSliceValue(allocSliceFromCell(cell))
        }

    val entries =
        listOf(
            sliceEntryOf(TvmConfigBoc.storagePricesEntryCell), // 18: storage_prices (current entry)
            sliceEntryOf(TvmConfigBoc.entries[19]), // 19: global_id
            sliceEntryOf(TvmConfigBoc.entries[20]), // 20: config_mc_gas_prices
            sliceEntryOf(TvmConfigBoc.entries[21]), // 21: config_gas_prices
            sliceEntryOf(TvmConfigBoc.entries[24]), // 24: config_mc_fwd_prices
            sliceEntryOf(TvmConfigBoc.entries[25]), // 25: config_fwd_prices
            sliceEntryOf(TvmConfigBoc.entries[43]), // 43: size_limits_config
        )

    return TvmStackTupleValueConcreteNew(
        ctx,
        entries.map { it.toStackEntry() }.toPersistentList(),
    )
}

fun TvmState.initContractInfo(
    contractCode: TsaContractCode,
    concreteData: TvmConcreteContractData,
    concreteGeneralData: TvmConcreteGeneralData,
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

        val randomSeed = TvmStackIntValue(with(ctx) { concreteGeneralData.initialSeed.toBv257() })

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

        // incomingValue is a Maybe Tuple of (Integer balance, Maybe Cell extra-currencies).
        // The proper value is set in [initializeContractExecutionMemory] when a message arrives.
        val incomingValue = makeIncomingValueEntry(ctx, zeroValue)

        // storagePhaseFees is symbolic and bounded by [MAX_STORAGE_PHASE_FEES]
        // (constraints are asserted below).
        val storagePhaseFeesValue =
            makeSymbolicPrimitive(mkBvSort(TvmContext.BITS_FOR_BALANCE), TvmStoragePhaseFees())
                .zeroExtendToSort(int257sort)
        val storagePhaseFees = TvmStackIntValue(storagePhaseFeesValue)

        // prevBlocksInfoTuple is a Maybe Tuple. Currently not modeled precisely, so it is null.
        val prevBlocksInfo = TvmStackNullValue

        // unpackedConfigTuple is a Maybe Tuple of slices over global config params (see [makeUnpackedConfigTuple]).
        val unpackedConfigTuple = makeUnpackedConfigTuple()

        // duePayment is symbolic and bounded by [MAX_DUE_PAYMENT]
        // (constraints are asserted below).
        val duePaymentValue =
            makeSymbolicPrimitive(mkBvSort(TvmContext.BITS_FOR_BALANCE), TvmDuePayment())
                .zeroExtendToSort(int257sort)
        val duePayment = TvmStackIntValue(duePaymentValue)

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
        pathConstraints += mkBvSignedLessExpr(storagePhaseFees.intValue, TvmContext.MAX_STORAGE_PHASE_FEES.toBv257())
        pathConstraints += mkBvSignedGreaterOrEqualExpr(duePayment.intValue, zeroValue)
        pathConstraints += mkBvSignedLessExpr(duePayment.intValue, TvmContext.MAX_DUE_PAYMENT.toBv257())
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
            addCellDictEntry(configDict, key, cellRef)
        }

        // now we patch the original config with the data to make the tests pass
        val misbehaviourPunishment = getMisbehaviorPunishment(this@initConfigRoot)
        addCellDictEntry(configDict, 40, misbehaviourPunishment)

        val dns = // TODO: find documentation
            allocCellFromFields(
                // TODO real dict
                mkBv(0, sizeBits = 1u), // ???
            )
        addCellDictEntry(configDict, 80, dns)
        configDict
    }

private fun getMisbehaviorPunishment(state: TvmState): UHeapRef {
    val ctx = state.ctx
    val hexBits = 4u
    val tagBits = hexBits * 2u
    val uint16Bits = 16u
    val uint32Bits = 32u
    val defaultFlatFineValue = BigInteger.valueOf(101) * BigInteger.valueOf(10).pow(9) // 101 TON
    val gramsLen = ctx.mkBv(5, sizeBits = 4u)
    val gramsValue = ctx.mkBv(defaultFlatFineValue, 5u * 8u)
    val defaultFlatFine = ctx.mkBvConcatExpr(gramsLen, gramsValue)

    // TODO get real values
    val punishmentSuffix = state.makeSymbolicPrimitive(ctx.mkBvSort(sizeBits = uint32Bits + 7u * uint16Bits))

    val misbehaviourPunishment =
        state.allocCellFromFields(
            ctx.mkBvHex(value = "01", tagBits), // misbehaviour_punishment_config_v1 tag
            defaultFlatFine, // default_flat_fine
            punishmentSuffix, // default_proportional_fine, severity_flat_mult, ...
        )
    return misbehaviourPunishment
}

private fun TvmState.allocCellFromFields(vararg fields: KExpr<KBvSort>): UHeapRef =
    with(ctx) {
        val data =
            fields.reduce { acc, field ->
                mkBvConcatExpr(acc, field)
            }

        allocDataCellFromData(data)
    }

private fun TvmState.allocDict(keyLength: Int): UConcreteHeapRef =
    with(ctx) {
        memory.allocConcrete(TvmDictCellType).also {
            memory.writeField(it, dictKeyLengthField, sizeSort, mkSizeExpr(keyLength), guard = trueExpr)
        }
    }

private fun TvmState.addCellDictEntry(
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
