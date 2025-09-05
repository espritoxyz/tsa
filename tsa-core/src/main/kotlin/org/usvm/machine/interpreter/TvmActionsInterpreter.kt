package org.usvm.machine.interpreter

import org.ton.bytecode.TvmAppActionsInst
import org.ton.bytecode.TvmAppActionsRawreserveInst
import org.ton.bytecode.TvmAppActionsSendmsgInst
import org.ton.bytecode.TvmAppActionsSendrawmsgInst
import org.ton.bytecode.TvmAppActionsSetcodeInst
import org.ton.bytecode.TvmCellValue
import org.ton.bytecode.TvmInst
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.C5Register
import org.usvm.machine.state.addInt
import org.usvm.machine.state.allocEmptyCell
import org.usvm.machine.state.builderStoreDataBits
import org.usvm.machine.state.builderStoreGrams
import org.usvm.machine.state.builderStoreInt
import org.usvm.machine.state.builderStoreNextRef
import org.usvm.machine.state.checkCellOverflow
import org.usvm.machine.state.checkOutOfRange
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.takeLastCell
import org.usvm.machine.state.takeLastIntOrThrowTypeError
import org.usvm.machine.state.unsignedIntegerFitsBits

class TvmActionsInterpreter(private val ctx: TvmContext) {
    fun visitActionsStmt(scope: TvmStepScopeManager, stmt: TvmAppActionsInst) {
        scope.consumeDefaultGas(stmt)

        when (stmt) {
            is TvmAppActionsSendrawmsgInst -> visitSendRawMsgInst(scope, stmt)
            is TvmAppActionsSendmsgInst -> visitSendMsgInst(scope, stmt)
            is TvmAppActionsRawreserveInst -> visitRawReserveInst(scope, stmt)
            is TvmAppActionsSetcodeInst -> visitSetCodeInst(scope, stmt)
            else -> TODO("$stmt")
        }
    }

    private fun visitSendRawMsgInst(scope: TvmStepScopeManager, stmt: TvmAppActionsSendrawmsgInst) = with(ctx) {
        val mode = scope.takeLastIntOrThrowTypeError()
            ?: return@with
        val msg = scope.calcOnState { takeLastCell() }
            ?: return@with scope.doWithState(throwTypeCheckError)

        val notOutOfRangeExpr = unsignedIntegerFitsBits(mode, 8u)
        checkOutOfRange(notOutOfRangeExpr, scope) ?: return

        doSendMsg(scope, msg, mode, stmt)
    }

    private fun visitSendMsgInst(scope: TvmStepScopeManager, stmt: TvmAppActionsSendmsgInst) = with(ctx) {
        val mode = scope.takeLastIntOrThrowTypeError()
            ?: return@with
        val msg = scope.calcOnState { takeLastCell() }
            ?: return@with scope.doWithState(throwTypeCheckError)

        val sendBit = sendMsgFeeEstimationFlag
        val sendFlag = mkBvAndExpr(mode, sendBit)
        val normalizedMode = mkBvSubExpr(mode, sendFlag)

        val notOutOfRangeExpr = unsignedIntegerFitsBits(normalizedMode, 8u)
        checkOutOfRange(notOutOfRangeExpr, scope) ?: return

        val fee = scope.calcOnState { makeSymbolicPrimitive(int257sort) }
        scope.assert(
            constraint = mkAnd(
                mkBvSignedLessExpr(zeroValue, fee),
                mkBvSignedLessOrEqualExpr(fee, maxMessageCurrencyValue)
            ),
            unsatBlock = {
                error("Cannot assume message fee constraints")
            }
        ) ?: return@with

        scope.doWithState {
            stack.addInt(fee)
        }

        scope.fork(
            condition = sendFlag eq zeroValue,
            falseStateIsExceptional = false,
            blockOnFalseState = {
                newStmt(stmt.nextStmt())
            }
        ) ?: return@with

        doSendMsg(scope, msg, normalizedMode, stmt)
    }

    private fun visitRawReserveInst(scope: TvmStepScopeManager, stmt: TvmAppActionsRawreserveInst) = with(ctx) {
        val mode = scope.takeLastIntOrThrowTypeError()
            ?: return@with
        val grams = scope.takeLastIntOrThrowTypeError()
            ?: return@with

        // TODO 4 or 5 bits depending on the version
        val modeNotOutOfRangeExpr = unsignedIntegerFitsBits(mode, 5u)
        val valueNotOutOfRangeExpr = mkBvSignedLessOrEqualExpr(zeroValue, grams)
        checkOutOfRange(modeNotOutOfRangeExpr and valueNotOutOfRangeExpr, scope) ?: return

        val notOutOfRangeGrams = unsignedIntegerFitsBits(grams, TvmContext.MAX_GRAMS_BITS)
        checkCellOverflow(notOutOfRangeGrams, scope) ?: return

        scope.doWithState {
            val registers = registersOfCurrentContract
            val actions = registers.c5.value.value
            val updatedActions = allocEmptyCell()

            builderStoreNextRef(updatedActions, actions)
            builderStoreDataBits(updatedActions, reserveActionTag)
            scope.builderStoreInt(updatedActions, updatedActions, mode, sizeBits = eightSizeExpr, isSigned = false) {
                error("Unexpected cell overflow during RAWRESERVE instruction")
            } ?: return@doWithState
            scope.builderStoreGrams(updatedActions, updatedActions, grams) ?: return@doWithState
            // empty ExtraCurrencyCollection
            builderStoreDataBits(updatedActions, zeroBit)

            registers.c5 = C5Register(TvmCellValue(updatedActions))

            newStmt(stmt.nextStmt())
        }
    }

    private fun doSendMsg(
        scope: TvmStepScopeManager,
        msg: UHeapRef,
        mode: UExpr<TvmInt257Sort>,
        stmt: TvmInst
    ): Unit = with(ctx) {
        scope.doWithStateCtx {
            val registers = registersOfCurrentContract
            val actions = registers.c5.value.value
            val updatedActions = allocEmptyCell()

            builderStoreNextRef(updatedActions, actions)
            builderStoreDataBits(updatedActions, sendMsgActionTag)
            scope.builderStoreInt(updatedActions, updatedActions, mode, sizeBits = eightSizeExpr, isSigned = false) {
                error("Unexpected cell overflow during $stmt instruction")
            } ?: return@doWithStateCtx
            builderStoreNextRef(updatedActions, msg)

            registers.c5 = C5Register(TvmCellValue(updatedActions))

            newStmt(stmt.nextStmt())
        }
    }

    private fun visitSetCodeInst(scope: TvmStepScopeManager, stmt: TvmAppActionsSetcodeInst) {
        scope.doWithState {
            val cell = takeLastCell()
                ?: ctx.throwTypeCheckError(this)

            // TODO make a real implementation
            newStmt(stmt.nextStmt())
        }
    }
}