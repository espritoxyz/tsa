package org.usvm.machine.interpreter

import org.ton.bytecode.ACTIONS_PARAMETER_IDX
import org.ton.bytecode.ADDRESS_PARAMETER_IDX
import org.ton.bytecode.BALANCE_PARAMETER_IDX
import org.ton.bytecode.BLOCK_TIME_PARAMETER_IDX
import org.ton.bytecode.CODE_PARAMETER_IDX
import org.ton.bytecode.CONFIG_PARAMETER_IDX
import org.ton.bytecode.DUE_PAYMENT_IDX
import org.ton.bytecode.MSGS_SENT_PARAMETER_IDX
import org.ton.bytecode.SEED_PARAMETER_IDX
import org.ton.bytecode.STORAGE_FEES_PARAMETER_IDX
import org.ton.bytecode.TAG_PARAMETER_IDX
import org.ton.bytecode.TIME_PARAMETER_IDX
import org.ton.bytecode.TRANSACTION_TIME_PARAMETER_IDX
import org.ton.bytecode.TvmAppConfigConfigoptparamInst
import org.ton.bytecode.TvmAppConfigGetforwardfeeInst
import org.ton.bytecode.TvmAppConfigGetforwardfeesimpleInst
import org.ton.bytecode.TvmAppConfigGetgasfeeInst
import org.ton.bytecode.TvmAppConfigGetoriginalfwdfeeInst
import org.ton.bytecode.TvmAppConfigGetparamInst
import org.ton.bytecode.TvmAppConfigGetprecompiledgasInst
import org.ton.bytecode.TvmAppConfigGetstoragefeeInst
import org.ton.bytecode.TvmAppConfigInst
import org.ton.bytecode.TvmInst
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.BIT_PRICE
import org.usvm.machine.state.BIT_PRICE_MASTERCHAIN
import org.usvm.machine.state.BIT_PRICE_PS
import org.usvm.machine.state.CELL_PRICE
import org.usvm.machine.state.CELL_PRICE_MASTERCHAIN
import org.usvm.machine.state.CELL_PRICE_PS
import org.usvm.machine.state.FIRST_FRAC
import org.usvm.machine.state.FIRST_FRAC_MASTERCHAIN
import org.usvm.machine.state.FLAT_GAS_LIMIT
import org.usvm.machine.state.FLAT_GAS_LIMIT_MASTERCHAIN
import org.usvm.machine.state.FLAT_GAS_PRICE
import org.usvm.machine.state.FLAT_GAS_PRICE_MASTERCHAIN
import org.usvm.machine.state.GAS_PRICE
import org.usvm.machine.state.GAS_PRICE_MASTERCHAIN
import org.usvm.machine.state.LUMP_PRICE
import org.usvm.machine.state.LUMP_PRICE_MASTERCHAIN
import org.usvm.machine.state.MC_BIT_PRICE_PS
import org.usvm.machine.state.MC_CELL_PRICE_PS
import org.usvm.machine.state.addInt
import org.usvm.machine.state.addOnStack
import org.usvm.machine.state.addTuple
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.configContainsParam
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.getCellContractInfoParam
import org.usvm.machine.state.getConfigParam
import org.usvm.machine.state.getContractInfoParam
import org.usvm.machine.state.getIntContractInfoParam
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.takeLastIntOrThrowTypeError
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmIntegerType
import org.usvm.machine.types.TvmNullType
import org.usvm.machine.types.TvmSliceType

class TvmConfigInterpreter(
    private val ctx: TvmContext,
) {
    fun visitConfigInst(
        scope: TvmStepScopeManager,
        stmt: TvmAppConfigInst,
    ) {
        scope.consumeDefaultGas(stmt)

        when (stmt) {
            is TvmAppConfigGetparamInst -> visitGetParamInst(scope, stmt)
            is TvmAppConfigConfigoptparamInst -> visitConfigParamInst(scope, stmt)
            is TvmAppConfigGetoriginalfwdfeeInst -> visitGetoriginalfwdfeeInst(scope, stmt)
            is TvmAppConfigGetprecompiledgasInst -> visitGetprecompiledgasInst(scope, stmt)
            is TvmAppConfigGetforwardfeesimpleInst -> visitGetforwardfeeInst(scope, stmt, isSimple = true)
            is TvmAppConfigGetforwardfeeInst -> visitGetforwardfeeInst(scope, stmt, isSimple = false)
            is TvmAppConfigGetstoragefeeInst -> visitGetstoragefeeInst(scope, stmt)
            is TvmAppConfigGetgasfeeInst -> visitConfigGetgasfeeInst(scope, stmt)
            else -> TODO("$stmt")
        }
    }

    private fun visitGetParamInst(
        scope: TvmStepScopeManager,
        stmt: TvmAppConfigGetparamInst,
    ) {
        scope.doWithStateCtx {
            when (val i = stmt.i) {
                TAG_PARAMETER_IDX -> { // TAG
                    val tag =
                        scope.getIntContractInfoParam(i)
                            ?: return@doWithStateCtx

                    stack.addInt(tag)
                }
                ACTIONS_PARAMETER_IDX -> { // ACTIONS
                    val actionNum =
                        scope.getIntContractInfoParam(i)
                            ?: return@doWithStateCtx

                    stack.addInt(actionNum)
                }
                MSGS_SENT_PARAMETER_IDX -> { // MSGS_SENT
                    val messagesSent =
                        scope.getIntContractInfoParam(i)
                            ?: return@doWithStateCtx

                    stack.addInt(messagesSent)
                }
                TIME_PARAMETER_IDX -> { // NOW
                    val now =
                        scope.getIntContractInfoParam(i)
                            ?: return@doWithStateCtx

                    stack.addInt(now)
                }
                BLOCK_TIME_PARAMETER_IDX -> { // BLOCK_LTIME
                    val blockLogicalTime =
                        scope.getIntContractInfoParam(i)
                            ?: return@doWithStateCtx

                    stack.addInt(blockLogicalTime)
                }
                TRANSACTION_TIME_PARAMETER_IDX -> { // LTIME
                    val logicalTime =
                        scope.getIntContractInfoParam(i)
                            ?: return@doWithStateCtx

                    stack.addInt(logicalTime)
                }
                SEED_PARAMETER_IDX -> { // RAND_SEED
                    val randomSeed =
                        scope.getIntContractInfoParam(i)
                            ?: return@doWithStateCtx

                    stack.addInt(randomSeed)
                }
                BALANCE_PARAMETER_IDX -> { // BALANCE
                    val balanceValue =
                        getContractInfoParam(i).tupleValue
                            ?: return@doWithStateCtx ctx.throwTypeCheckError(this)

                    stack.addTuple(balanceValue)
                }
                ADDRESS_PARAMETER_IDX -> { // MYADDR
                    val cell =
                        scope.getCellContractInfoParam(i)
                            ?: return@doWithStateCtx

                    val slice = scope.calcOnState { allocSliceFromCell(cell) }
                    addOnStack(slice, TvmSliceType)
                }
                CONFIG_PARAMETER_IDX -> { // GLOBAL_CONFIG
                    val cell =
                        scope.getCellContractInfoParam(i)
                            ?: return@doWithStateCtx

                    addOnStack(cell, TvmCellType)
                }
                CODE_PARAMETER_IDX -> { // MYCODE
                    val cell =
                        getContractInfoParam(i).cellValue
                            ?: return@doWithStateCtx ctx.throwTypeCheckError(this)

                    addOnStack(cell, TvmCellType)
                }
                DUE_PAYMENT_IDX -> {
                    val duePayment =
                        scope.getIntContractInfoParam(i)
                            ?: return@doWithStateCtx

                    stack.addInt(duePayment)
                }
                STORAGE_FEES_PARAMETER_IDX -> {
                    val storageFee =
                        scope.getIntContractInfoParam(i)
                            ?: return@doWithStateCtx

                    stack.addInt(storageFee)
                }
                else -> TODO("$i GETPARAM")
            }

            newStmt(stmt.nextStmt())
        }
    }

    private fun visitConfigParamInst(
        scope: TvmStepScopeManager,
        stmt: TvmAppConfigConfigoptparamInst,
    ) = with(ctx) {
        val idx = scope.takeLastIntOrThrowTypeError() ?: return@with

        val absIdx = mkIte(mkBvSignedGreaterOrEqualExpr(idx, zeroValue), idx, mkBvNegationExpr(idx))

        val configContainsIdx = scope.calcOnState { configContainsParam(absIdx) }
        scope.assert(
            configContainsIdx,
            unsatBlock = { error("Config doesn't contain idx: $absIdx") },
        ) ?: return@with

        val result =
            scope.getConfigParam(absIdx)
                ?: return@with

        scope.doWithState {
            scope.addOnStack(result, TvmCellType)
            newStmt(stmt.nextStmt())
        }
    }

    private fun checkInRange(
        scope: TvmStepScopeManager,
        value: UExpr<TvmContext.TvmInt257Sort>,
        min: ULong?,
        max: ULong?,
    ): Unit? =
        with(ctx) {
            var cond: UBoolExpr = trueExpr
            if (min != null) {
                cond = cond and mkBvSignedGreaterOrEqualExpr(value, min.toBv().unsignedExtendToInteger())
            }
            if (max != null) {
                cond = cond and mkBvSignedLessOrEqualExpr(value, max.toBv().unsignedExtendToInteger())
            }

            return scope.fork(
                cond,
                falseStateIsExceptional = true,
                blockOnFalseState = throwIntegerOutOfRangeError,
            )
        }

    private fun visitGetoriginalfwdfeeInst(
        scope: TvmStepScopeManager,
        stmt: TvmAppConfigGetoriginalfwdfeeInst,
    ) = with(ctx) {
        // in case [firstFrac] and [firstFracMasterchain] become different at some point
        check(FIRST_FRAC == FIRST_FRAC_MASTERCHAIN) {
            "Different values of firstFrac for basechain and masterchain are not supported"
        }
        // skip is_masterchain
        scope.takeLastIntOrThrowTypeError()
            ?: return@with

        val fwdFee =
            scope.takeLastIntOrThrowTypeError()
                ?: return@with

        checkInRange(scope, fwdFee, min = 0UL, max = null)
            ?: return@with

        val workingSort = mkBvSort(int257sort.sizeBits + 17u)
        val fwdFeeExtended = fwdFee.zeroExtendToSort(workingSort)
        val mul = mkBvShiftLeftExpr(mkBv(1, workingSort), mkBv(16, workingSort)) // 2^16
        val div = mkBvSubExpr(mul, mkBv(FIRST_FRAC, workingSort))

        val resultExtended = makeDiv(mkBvMulExpr(fwdFeeExtended, mul), div)
        // workingSort should be enough for overflow not to happen
        checkInBounds(resultExtended.value, scope)
            ?: return@with

        val result = resultExtended.value.extractToInt257Sort()
        scope.doWithState {
            stack.addInt(result)
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitGetprecompiledgasInst(
        scope: TvmStepScopeManager,
        stmt: TvmAppConfigGetprecompiledgasInst,
    ) {
        // TODO: honest implementation (with config)
        scope.doWithState {
            addOnStack(ctx.nullValue, TvmNullType)
            newStmt(stmt.nextStmt())
        }
    }

    private fun forkOnMasterchain(
        scope: TvmStepScopeManager,
        isMasterchainInt: UExpr<TvmContext.TvmInt257Sort>,
        performAction: TvmStepScopeManager.(Boolean) -> Unit,
    ) = with(ctx) {
        val isMasterchainSymbolic = (isMasterchainInt eq zeroValue).not()
        scope.doWithConditions(
            listOf(
                TvmStepScopeManager.ActionOnCondition(
                    condition = isMasterchainSymbolic,
                    caseIsExceptional = false,
                    paramForDoForAllBlock = true,
                    action = {},
                ),
                TvmStepScopeManager.ActionOnCondition(
                    condition = isMasterchainSymbolic.not(),
                    caseIsExceptional = false,
                    paramForDoForAllBlock = false,
                    action = {},
                ),
            ),
            performAction,
        )
    }

    private val maxValue: ULong = (1UL shl 63) - 1UL

    private fun visitGetforwardfeeInst(
        scope: TvmStepScopeManager,
        stmt: TvmInst,
        isSimple: Boolean,
    ) = with(ctx) {
        val isMasterchainInt =
            scope.takeLastIntOrThrowTypeError()
                ?: return

        val bits =
            scope.takeLastIntOrThrowTypeError()
                ?: return
        checkInRange(scope, bits, min = 0UL, max = maxValue)
            ?: return@with

        val cells =
            scope.takeLastIntOrThrowTypeError()
                ?: return
        checkInRange(scope, cells, min = 0UL, max = maxValue)
            ?: return@with

        forkOnMasterchain(scope, isMasterchainInt) { isMasterchain ->
            val configBitPrice = if (isMasterchain) BIT_PRICE_MASTERCHAIN else BIT_PRICE
            val configCellPrice = if (isMasterchain) CELL_PRICE_MASTERCHAIN else CELL_PRICE
            val configLumpPrice = if (isMasterchain) LUMP_PRICE_MASTERCHAIN else LUMP_PRICE

            val configBitPriceSymbolic = mkBv(configBitPrice, int257sort)
            val configCellPriceSymbolic = mkBv(configCellPrice, int257sort)

            val div = mkBvShiftLeftExpr(oneValue, mkBv(16, int257sort)) // 2^16

            // int257sort should be enough for overflow not to happen
            val simpleFee =
                makeDivc(
                    mkBvAddExpr(
                        mkBvMulExpr(
                            bits,
                            configBitPriceSymbolic,
                        ),
                        mkBvMulExpr(
                            cells,
                            configCellPriceSymbolic,
                        ),
                    ),
                    div,
                )

            val result =
                if (isSimple) {
                    simpleFee.value
                } else {
                    val configLumpPriceSymbolic = mkBv(configLumpPrice, int257sort)
                    mkBvAddExpr(configLumpPriceSymbolic, simpleFee.value)
                }
            addOnStack(result, TvmIntegerType)

            doWithState {
                newStmt(stmt.nextStmt())
            }
        }
    }

    // formula:
    // https://docs.ton.org/v3/documentation/smart-contracts/transaction-fees/fees-low-level#formula
    // https://github.com/ton-blockchain/ton/blob/050a984163a53df16fb03f66cc445c34bfed48ed/crypto/vm/tonops.cpp#L2137
    private fun visitGetstoragefeeInst(
        scope: TvmStepScopeManager,
        stmt: TvmAppConfigGetstoragefeeInst,
    ) = with(ctx) {
        val isMasterchainInt =
            scope.takeLastIntOrThrowTypeError()
                ?: return

        val seconds =
            scope.takeLastIntOrThrowTypeError()
                ?: return
        checkInRange(scope, seconds, min = 0UL, max = maxValue)
            ?: return@with

        val bits =
            scope.takeLastIntOrThrowTypeError()
                ?: return
        checkInRange(scope, bits, min = 0UL, max = maxValue)
            ?: return@with

        val cells =
            scope.takeLastIntOrThrowTypeError()
                ?: return
        checkInRange(scope, cells, min = 0UL, max = maxValue)
            ?: return@with

        forkOnMasterchain(scope, isMasterchainInt) { isMasterchain ->
            val configCellPrice = if (isMasterchain) MC_CELL_PRICE_PS else CELL_PRICE_PS
            val configBitPrice = if (isMasterchain) MC_BIT_PRICE_PS else BIT_PRICE_PS

            val configBitPriceSymbolic = mkBv(configBitPrice, int257sort)
            val configCellPriceSymbolic = mkBv(configCellPrice, int257sort)

            val div = mkBvShiftLeftExpr(oneValue, mkBv(16, int257sort)) // 2^16

            // int257sort should be enough for overflow not to happen
            val result =
                makeDivc(
                    mkBvMulExpr(
                        mkBvAddExpr(
                            mkBvMulExpr(
                                bits,
                                configBitPriceSymbolic,
                            ),
                            mkBvMulExpr(
                                cells,
                                configCellPriceSymbolic,
                            ),
                        ),
                        seconds,
                    ),
                    div,
                )
            addOnStack(result.value, TvmIntegerType)

            doWithState {
                newStmt(stmt.nextStmt())
            }
        }
    }

    // formula: https://github.com/ton-blockchain/ton/blob/050a984163a53df16fb03f66cc445c34bfed48ed/crypto/block/mc-config.h#L354
    private fun visitConfigGetgasfeeInst(
        scope: TvmStepScopeManager,
        stmt: TvmAppConfigGetgasfeeInst,
    ) = with(ctx) {
        val isMasterchainInt =
            scope.takeLastIntOrThrowTypeError()
                ?: return

        val gasUsed =
            scope.takeLastIntOrThrowTypeError()
                ?: return
        checkInRange(scope, gasUsed, min = 0UL, max = maxValue)
            ?: return@with

        forkOnMasterchain(scope, isMasterchainInt) { isMasterchain ->
            val configFlatGasLimit = if (isMasterchain) FLAT_GAS_LIMIT_MASTERCHAIN else FLAT_GAS_LIMIT
            val configFlatGasPrice = if (isMasterchain) FLAT_GAS_PRICE_MASTERCHAIN else FLAT_GAS_PRICE
            val configGasPrice = if (isMasterchain) GAS_PRICE_MASTERCHAIN else GAS_PRICE

            val useFlatGas = mkBvSignedLessOrEqualExpr(gasUsed, configFlatGasLimit.toBv257())
            val flatGasExpr = configFlatGasPrice.toBv257()

            val configFlatGasLimitSymbolic = mkBv(configFlatGasLimit, int257sort)
            val configFlatGasPriceSymbolic = mkBv(configFlatGasPrice, int257sort)
            val configGasPriceExtended = mkBv(configGasPrice, int257sort)
            val div = mkBvShiftLeftExpr(oneValue, mkBv(16, int257sort)) // 2^16

            // int257sort should be enough for overflow not to happen
            val nonFlatPrice =
                mkBvAddExpr(
                    configFlatGasPriceSymbolic,
                    makeDivc(
                        mkBvMulExpr(
                            mkBvSubExpr(
                                gasUsed,
                                configFlatGasLimitSymbolic,
                            ),
                            configGasPriceExtended,
                        ),
                        div,
                    ).value,
                )

            val result =
                mkIte(
                    useFlatGas,
                    trueBranch = { flatGasExpr },
                    falseBranch = { nonFlatPrice },
                )

            addOnStack(result, TvmIntegerType)

            doWithState {
                newStmt(stmt.nextStmt())
            }
        }
    }
}
