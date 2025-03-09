package org.usvm.machine.interpreter

import org.ton.bytecode.TvmAppCryptoChksignuInst
import org.ton.bytecode.TvmAppCryptoHashcuInst
import org.ton.bytecode.TvmAppCryptoHashsuInst
import org.ton.bytecode.TvmAppCryptoInst
import org.usvm.UHeapRef
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmStack
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.addInt
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.slicePreloadDataBits
import org.usvm.machine.state.takeLastBuilder
import org.usvm.machine.state.takeLastCell
import org.usvm.machine.state.takeLastIntOrThrowTypeError
import org.usvm.machine.state.takeLastSlice
import org.usvm.machine.state.unsignedIntegerFitsBits
import org.usvm.machine.types.TvmBuilderType
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmRealReferenceType
import org.usvm.machine.types.TvmSliceType

class TvmCryptoInterpreter(private val ctx: TvmContext) {
    fun visitCryptoStmt(scope: TvmStepScopeManager, stmt: TvmAppCryptoInst) {
        when (stmt) {
            is TvmAppCryptoHashsuInst -> visitSingleHashInst(scope, stmt, operandType = TvmSliceType)
            is TvmAppCryptoHashcuInst -> visitSingleHashInst(scope, stmt, operandType = TvmCellType)
            is TvmAppCryptoChksignuInst -> visitCheckSignatureInst(scope, stmt)
            else -> TODO("$stmt")
        }
    }

    private fun visitSingleHashInst(
        scope: TvmStepScopeManager,
        stmt: TvmAppCryptoInst,
        operandType: TvmRealReferenceType
    ) {
        require(operandType != TvmBuilderType) {
            "A single hash function for builders does not exist"
        }

        scope.consumeDefaultGas(stmt)

        scope.calcOnState {
            val value = stack.popHashableStackValue(operandType)
                ?: return@calcOnState

            val hash = addressToHash[value] ?: run {
                val res = makeSymbolicPrimitive(ctx.int257sort)
                addressToHash = addressToHash.put(value, res)
                res
            }

            // Hash is a 256-bit unsigned integer
            scope.assert(
                ctx.unsignedIntegerFitsBits(hash, 256u),
                unsatBlock = { error("Cannot make hash fit in 256 bits") }
            ) ?: return@calcOnState

            stack.addInt(hash)
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitCheckSignatureInst(scope: TvmStepScopeManager, stmt: TvmAppCryptoChksignuInst) {
        scope.consumeDefaultGas(stmt)

        val key = scope.takeLastIntOrThrowTypeError()
        val signature = scope.calcOnState { stack.takeLastSlice() }
        if (signature == null) {
            scope.doWithState(ctx.throwTypeCheckError)
            return
        }

        val hash = scope.takeLastIntOrThrowTypeError()

        // Check that signature is correct - it contains at least 512 bits
        val bits = scope.slicePreloadDataBits(signature, bits = 512)
        if (bits == null) {
            scope.doWithStateCtx {
                throwUnknownCellUnderflowError(this)
            }

            return
        }

        // TODO do real check?
        val condition = scope.calcOnState { makeSymbolicPrimitive(ctx.boolSort) }
        with(ctx) {
            scope.fork(
                condition,
                falseStateIsExceptional = false,
                blockOnTrueState = {
                    stack.addInt(zeroValue)
                    newStmt(stmt.nextStmt())
                },
                blockOnFalseState = {
                    stack.addInt(minusOneValue)
                    newStmt(stmt.nextStmt())
                }
            )
        }
    }

    context(TvmState)
    private fun TvmStack.popHashableStackValue(referenceType: TvmRealReferenceType): UHeapRef? =
        when (referenceType) {
            TvmBuilderType -> takeLastBuilder()
            TvmCellType -> takeLastCell()
            TvmSliceType -> takeLastSlice()
        }
}
