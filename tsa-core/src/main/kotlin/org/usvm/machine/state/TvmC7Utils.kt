package org.usvm.machine.state

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBvSort
import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.ton.bytecode.BALANCE_PARAMETER_IDX
import org.ton.bytecode.TsaContractCode
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TvmContext.Companion.ADDRESS_BITS
import org.usvm.machine.TvmContext.Companion.CONFIG_KEY_LENGTH
import org.usvm.machine.TvmContext.Companion.INT_BITS
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
import java.math.BigInteger
import org.usvm.UConcreteHeapRef
import org.usvm.api.writeField
import org.usvm.machine.TvmContext.Companion.HASH_BITS
import org.usvm.machine.TvmContext.Companion.dictKeyLengthField


fun TvmState.getContractInfoParam(idx: Int): TvmStackValue {
    require(idx in 0..17) {
        "Unexpected param index $idx"
    }

    return getContractInfo()[idx, stack].cell(stack)
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
    getContractInfoParam(BALANCE_PARAMETER_IDX).tupleValue
        ?.get(0, stack)?.cell(stack)?.intValue

fun TvmState.setContractInfoParam(idx: Int, value: TvmStackEntry) {
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
    val sliceValue = calcOnStateCtx {
        dictGetValue(
            configDict,
            DictId(CONFIG_KEY_LENGTH),
            idx.extractToSort(mkBvSort(CONFIG_KEY_LENGTH.toUInt())),
        )
    }

    return slicePreloadNextRef(sliceValue)
}

fun TvmState.configContainsParam(idx: UExpr<TvmInt257Sort>): UBoolExpr = with(ctx) {
    val configDict = getConfig()

    dictContainsKey(
        configDict,
        DictId(CONFIG_KEY_LENGTH),
        idx.extractToSort(mkBvSort(CONFIG_KEY_LENGTH.toUInt()))
    )
}

fun TvmState.getGlobalVariable(idx: Int, stack: TvmStack): TvmStackValue {
    require(idx in 0..< 255) {
        "Unexpected global variable with index $idx"
    }
    val registers = registersOfCurrentContract
    val globalEntries = registers.c7.value.entries.extendToSize(idx + 1)

    return globalEntries.getOrNull(idx)?.cell(stack)
        ?: error("Cannot find global variable with index $idx")
}

fun TvmState.setGlobalVariable(idx: Int, value: TvmStackEntry) {
    require(idx in 0..< 255) {
        "Unexpected setting global variable with index $idx"
    }

    val registers = registersOfCurrentContract
    val updatedC7 = TvmStackTupleValueConcreteNew(
        ctx,
        registers.c7.value.entries.extendToSize(idx + 1)
    ).set(idx, value)

    registers.c7 = C7Register(updatedC7)
}

fun TvmState.initC7(contractInfo: TvmStackTupleValue): TvmStackTupleValueConcreteNew =
    TvmStackTupleValueConcreteNew(
        ctx,
        persistentListOf(contractInfo.toStackEntry())
    )

const val bitPricePs = 1
const val cellPricePs = 500
const val mcBitPricePs = 1000
const val mcCellPricePs = 500000

const val lumpPriceMasterchain = 10000000
const val firstFracMasterchain = 21845
const val bitPriceMasterchain = 655360000
const val cellPriceMasterchain = 65536000000
const val flatGasLimitMasterchain = 100
const val flatGasPriceMasterchain = 1000000
const val gasPriceMasterchain = 655360000

const val lumpPrice = 400000
const val firstFrac = 21845
const val bitPrice = 26214400
const val cellPrice = 2621440000
const val flatGasLimit = 100
const val flatGasPrice = 40000
const val gasPrice = 26214400

fun TvmState.initContractInfo(
    contractCode: TsaContractCode,
): TvmStackTupleValueConcreteNew = with(ctx) {
    val tag = TvmStackIntValue(mkBvHex("076ef1ea", sizeBits = INT_BITS).uncheckedCast())

    // Right now, this parameter can only be set to zero in emulator
    // https://github.com/ton-blockchain/ton/blob/59a8cf0ae5c3062d14ec4c89a04fee80b5fd05c1/crypto/smc-envelope/SmartContract.cpp#L153
    val actions = TvmStackIntValue(zeroValue)

    // Right now, this parameter can only be set to zero in emulator
    // https://github.com/ton-blockchain/ton/blob/59a8cf0ae5c3062d14ec4c89a04fee80b5fd05c1/crypto/smc-envelope/SmartContract.cpp#L154
    val msgsSent = TvmStackIntValue(zeroValue)

    val unixTime = TvmStackIntValue(makeSymbolicPrimitive(int257sort))

    // Right now, this parameter can only be set to zero in emulator
    // https://github.com/ton-blockchain/ton/blob/59a8cf0ae5c3062d14ec4c89a04fee80b5fd05c1/crypto/smc-envelope/SmartContract.cpp#L156
    val blockLogicTime = TvmStackIntValue(zeroValue)

    // Right now, this parameter can only be set to zero in emulator
    // https://github.com/ton-blockchain/ton/blob/59a8cf0ae5c3062d14ec4c89a04fee80b5fd05c1/crypto/smc-envelope/SmartContract.cpp#L157
    val transactionLogicTime = TvmStackIntValue(zeroValue)

    val randomSeed = TvmStackIntValue(makeSymbolicPrimitive(int257sort))
    val grams = makeSymbolicPrimitive(int257sort)
    val balance = TvmStackTupleValueConcreteNew(
        ctx,
        persistentListOf(
            TvmStackIntValue(grams).toStackEntry(),
            TvmStackNullValue.toStackEntry(),
        )
    )
    val workchain = makeSymbolicPrimitive(mkBv8Sort())
    val extendedWorkchain = workchain.signedExtendToInteger()
    val addr = TvmStackCellValue(
        allocDataCellFromData(
            mkBvConcatExpr(
                mkBvConcatExpr(
                    // addr_std$10 anycast:(Maybe Anycast)
                    mkBv("100", 3u),
                    // workchain_id:int8
                    workchain
                ),
                // address:bits256
                makeSymbolicPrimitive(mkBvSort(ADDRESS_BITS.toUInt()))
            )
        )
    )
    val config = TvmStackCellValue(initConfigRoot())
    val code = TvmStackCellValue(allocateCell(contractCode.codeCell))

    // TODO support `incomingValue` param
    val incomingValue = TvmStackNullValue

    // Right now, this parameter can only be set to zero in emulator
    // https://github.com/ton-blockchain/ton/blob/59a8cf0ae5c3062d14ec4c89a04fee80b5fd05c1/crypto/smc-envelope/SmartContract.cpp#L166
    val storagePhaseFees = TvmStackIntValue(makeSymbolicPrimitive(int257sort))

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
    pathConstraints += mkBvSignedLessOrEqualExpr(unitTimeMinValue, unixTime.intValue)
    pathConstraints += mkBvSignedLessOrEqualExpr(unixTime.intValue, unitTimeMaxValue)
    pathConstraints += mkBvSignedGreaterOrEqualExpr(blockLogicTime.intValue, zeroValue)
    pathConstraints += mkBvSignedGreaterExpr(maxTimestampValue, blockLogicTime.intValue)
    pathConstraints += mkBvSignedGreaterOrEqualExpr(transactionLogicTime.intValue, zeroValue)
    pathConstraints += mkBvSignedGreaterExpr(maxTimestampValue, transactionLogicTime.intValue)
    pathConstraints += mkBvSignedGreaterOrEqualExpr(grams, zeroValue)
    pathConstraints += mkBvSignedGreaterOrEqualExpr(storagePhaseFees.intValue, zeroValue)
    pathConstraints += mkAnd((extendedWorkchain eq masterchain) or (extendedWorkchain eq baseChain))

    val paramList = listOf(
        tag, actions, msgsSent, unixTime, blockLogicTime, transactionLogicTime, randomSeed,
        balance, addr, config, code, incomingValue, storagePhaseFees, prevBlocksInfo,
        unpackedConfigTuple, duePayment, gasUsageIfPrecompiled
    )

    TvmStackTupleValueConcreteNew(
        ctx,
        paramList.map { it.toStackEntry() }.toPersistentList()
    )
}

private fun TvmState.initConfigRoot(): UHeapRef = with(ctx) {
    val configDict = allocDict(keyLength = CONFIG_KEY_LENGTH)

    val hexBits = 4u
    val hexAddressBits = ADDRESS_BITS / hexBits.toInt()
    val addressBits = ADDRESS_BITS.toUInt()
    val tagBits = hexBits * 2u
    val uint8Bits = 8u
    val uint16Bits = 16u
    val uint32Bits = 32u
    val uint64Bits = 64u

    // https://explorer.toncoin.org/config
    // https://tonviewer.com/config
    // https://github.com/ton-blockchain/ton/blob/d2b418bb703ed6ccd89b7d40f9f1e44686012014/crypto/block/block.tlb

    /**
     * Index: 0
     */
    val configAddr = mkBvHex("5".repeat(hexAddressBits), addressBits)
    addDictEntry(configDict, 0, allocDataCellFromData(configAddr))

    /**
     * Index: 1
     */
    val electorAddr = mkBvHex("3".repeat(hexAddressBits), addressBits)
    addDictEntry(configDict, 1, allocDataCellFromData(electorAddr))

    /**
     * Index: 12
     */
    val workchainDescr = allocCellFromFields(
        mkBvHex("a6", tagBits),                                                                 // workchain tag
        mkBv(1573821854, uint32Bits),                                                           // enabled_since
        mkBv(0, uint8Bits),                                                                     // actual_min_split
        mkBv(2, uint8Bits),                                                                     // min_split
        mkBv(8, uint8Bits),                                                                     // max_split
        mkBv(7, sizeBits = 3u),                                                                 // basic active accept_msgs
        mkBv(0, sizeBits = 13u),                                                                // flags
        mkBvHex("55b13f6d0e1d0c34c9c2160f6f918e92d82bf9ddcf8de2e4c94a3fdf39d15446", HASH_BITS), // zerostate_root_hash
        mkBvHex("ee0bedfe4b32761fb35e9e1d8818ea720cad1a0e7b4d2ed673c488e72e910342", HASH_BITS), // zerostate_file_hash
        mkBv(0, sizeBits = 32u),                                                                // version
        mkBvHex("1", hexBits),                                                                  // wfmt_basic tag
        mkBv(-1, uint32Bits),                                                                   // vm_version
        mkBv(0, uint64Bits),                                                                    // vm_mode
    )
    val workchainsDict = allocDict(keyLength = 32)
    addDictEntry(workchainsDict, 0, allocSliceFromCell(workchainDescr), isCellValue = false)

    val workchainsMaybeDict = allocDataCellFromData(oneBit)
    builderStoreNextRef(workchainsMaybeDict, workchainsDict)

    addDictEntry(configDict, 12, workchainsMaybeDict)

    /**
     * Index: 15
     */
    val elections = allocCellFromFields(
        mkBv(65536, uint32Bits),   // validators_elected_for
        mkBv(32768, uint32Bits),   // elections_start_before
        mkBv(8192, uint32Bits),    // elections_end_before
        mkBv(32768, uint32Bits),   // stake_held_for
    )
    addDictEntry(configDict, 15, elections)

    /**
     * Index: 18
     */
    val storagePrices = allocCellFromFields(
        mkBvHex(value = "cc", tagBits),     // gas_prices tag
        mkBv(value = 0, uint32Bits),        // utime_since
        mkBv(bitPricePs, uint64Bits),       // bit_price_ps
        mkBv(cellPricePs, uint64Bits),      // cell_price_ps
        mkBv(mcBitPricePs, uint64Bits),     // mc_bit_price_ps
        mkBv(mcCellPricePs, uint64Bits),    // mc_cell_price_ps
    )
    val storagePricesSlice = allocSliceFromCell(storagePrices)
    val storagePricesDict = allocDict(keyLength = 32)

    addDictEntry(storagePricesDict, 0, storagePricesSlice, isCellValue = false)
    addDictEntry(configDict, 18, storagePricesDict)

    /**
     * Index: 20
     */
    val masterchainGasPrices = allocCellFromFields(
        mkBvHex("d1", tagBits),               // gas_flat_pfx tag
        mkBv(flatGasLimitMasterchain, uint64Bits),  // flag_gas_limit
        mkBv(flatGasPriceMasterchain, uint64Bits),  // flag_gas_price
        mkBvHex("de", tagBits),               // gas_prices_ext tag
        mkBv(gasPriceMasterchain, uint64Bits),      // gas_price
        mkBv(1000000, uint64Bits),            // gas_limit
        mkBv(35000000, uint64Bits),           // special_gas_limit
        mkBv(10000, uint64Bits),              // gas_credit
        mkBv(2500000, uint64Bits),            // block_gas_limit
        mkBv(100000000, uint64Bits),          // freeze_due_limit
        mkBv(1000000000, uint64Bits),         // delete_due_limit
    )
    addDictEntry(configDict, 20, masterchainGasPrices)

    /**
     * Index: 21
     */
    val gasPrices = allocCellFromFields(
        mkBvHex("d1", tagBits),         // gas_flat_pfx tag
        mkBv(flatGasLimit, uint64Bits),       // flag_gas_limit
        mkBv(flatGasPrice, uint64Bits),       // flag_gas_price
        mkBvHex("de", tagBits),         // gas_prices_ext tag
        mkBv(gasPrice, uint64Bits),           // gas_price
        mkBv(1000000, uint64Bits),      // gas_limit
        mkBv(1000000, uint64Bits),      // special_gas_limit
        mkBv(10000, uint64Bits),        // gas_credit
        mkBv(10000000, uint64Bits),     // block_gas_limit
        mkBv(100000000, uint64Bits),    // freeze_due_limit
        mkBv(1000000000, uint64Bits),   // delete_due_limit
    )
    addDictEntry(configDict, 21, gasPrices)

    /**
     * Index: 24
     */
    val masterchainMsgPrices = allocCellFromFields(
        mkBvHex("ea", tagBits),             // msg_forward_prices tag
        mkBv(lumpPriceMasterchain, uint64Bits),   // lump_price
        mkBv(bitPriceMasterchain, uint64Bits),    // bit_price
        mkBv(cellPriceMasterchain, uint64Bits),   // cell_price
        mkBv(98304, uint32Bits),            // ihr_price_factor
        mkBv(firstFracMasterchain, uint16Bits),   // first_frac
        mkBv(21845, uint16Bits),            // next_frac
    )
    addDictEntry(configDict, 24, masterchainMsgPrices)

    /**
     * Index: 25
     */
    val msgPrices = allocCellFromFields(
        mkBvHex("ea", tagBits),         // msg_forward_prices tag
        mkBv(lumpPrice, uint64Bits),          // lump_price
        mkBv(bitPrice, uint64Bits),           // bit_price
        mkBv(cellPrice, uint64Bits),          // cell_price
        mkBv(98304, uint32Bits),        // ihr_price_factor
        mkBv(firstFrac, uint16Bits),          // first_frac
        mkBv(21845, uint16Bits),        // next_frac
    )
    addDictEntry(configDict, 25, msgPrices)

    /**
     * Index: 34
     */
    val validatorSet = allocCellFromFields(
        mkBvHex("12", tagBits),                 // validators_ext tag
        mkBv(1717587720, uint32Bits),           // utime_since
        mkBv(1717653256, uint32Bits),           // utime_until
        mkBv(345, uint16Bits),                  // total
        mkBv(100, uint16Bits),                  // main
        mkBv(1152921504606846802, uint64Bits),  // total_weight
        // TODO real dict
        mkBv(0, sizeBits = 1u),                 // list
    )
    addDictEntry(configDict, 34, validatorSet)

    /**
     * Index: 40
     */
    val defaultFlatFineValue = BigInteger.valueOf(101) * BigInteger.valueOf(10).pow(9) // 101 TON
    val gramsLen = mkBv(5, sizeBits = 4u)
    val gramsValue = mkBv(defaultFlatFineValue, 5u * 8u)
    val defaultFlatFine = mkBvConcatExpr(gramsLen, gramsValue)

    // TODO get real values
    val punishmentSuffix = makeSymbolicPrimitive(mkBvSort(sizeBits = uint32Bits + 7u * uint16Bits))

    val misbehaviourPunishment = allocCellFromFields(
        mkBvHex(value = "01", tagBits), // misbehaviour_punishment_config_v1 tag
        defaultFlatFine,                // default_flat_fine
        punishmentSuffix                // default_proportional_fine, severity_flat_mult, ...
    )
    addDictEntry(configDict, 40, misbehaviourPunishment)

    /**
     * Index: 71
     */
    val ethereumBridge = allocCellFromFields(
        mkBvHex("dd24c4a1f2b88f8b7053513b5cc6c5a31bc44b2a72dcb4d8c0338af0f0d37ec5", addressBits), // bridge_addr
        mkBvHex("3b9bbfd0ad5338b9700f0833380ee17d463e51c1ae671ee6f08901bde899b202", addressBits), // oracle_multisig_address
        // TODO real dict
        mkBv(0, sizeBits = 1u),                                                                     // oracles
        mkBvHex("000000000000000000000000582d872a1b094fc48f5de31d3b73f2d9be47def1", addressBits), // external_chain_address
    )
    addDictEntry(configDict, 71, ethereumBridge)

    /**
     * Index: 80
     */
    // TODO: find documentation
    val dns = allocCellFromFields(
        // TODO real dict
        mkBv(0, sizeBits = 1u),     // ???
    )
    addDictEntry(configDict, 80, dns)

    configDict
}

private fun TvmState.allocCellFromFields(vararg fields: KExpr<KBvSort>): UHeapRef = with(ctx) {
    val data = fields.reduce { acc, field ->
        mkBvConcatExpr(acc, field)
    }

    allocDataCellFromData(data)
}

private fun TvmState.allocDict(keyLength: Int): UConcreteHeapRef = with(ctx) {
    memory.allocConcrete(TvmDictCellType).also {
        memory.writeField(it, dictKeyLengthField, int257sort, keyLength.toBv257(), guard = trueExpr)
    }
}

private fun TvmState.addDictEntry(
    dict: UHeapRef,
    key: Int,
    value: UHeapRef,
    isCellValue: Boolean = true
) = with(ctx) {
    val sliceValue = if (isCellValue) {
        val builder = allocEmptyCell().also { builderStoreNextRef(it, value) }
        allocSliceFromCell(builder)
    } else {
        value
    }

    dictAddKeyValue(
        dict,
        DictId(CONFIG_KEY_LENGTH),
        mkBv(key, CONFIG_KEY_LENGTH.toUInt()),
        sliceValue,
    )
}

private fun TvmState.getContractInfo() = registersOfCurrentContract.c7.value[0, stack].cell(stack)?.tupleValue
    ?: error("Unexpected contract info value")

private fun TvmState.getConfig() = getContractInfo()[9, stack].cell(stack)?.cellValue
    ?: error("Unexpected config value")

private fun PersistentList<TvmStackEntry>.extendToSize(newSize: Int): PersistentList<TvmStackEntry> {
    if (size >= newSize) {
        return this
    }

    val newValuesSize = newSize - size
    val newValues = List(newValuesSize) { TvmStackNullValue.toStackEntry() }

    return addAll(newValues)
}
