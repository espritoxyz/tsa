package org.usvm.machine.interpreter

import org.ton.bytecode.TvmAppCryptoChksignuInst
import org.ton.bytecode.TvmAppCryptoHashcuInst
import org.ton.bytecode.TvmAppCryptoHashextSha256Inst
import org.ton.bytecode.TvmAppCryptoHashsuInst
import org.ton.bytecode.TvmAppCryptoInst
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.intValue
import org.usvm.machine.interpreter.TvmInterpreter.Companion.logger
import org.usvm.machine.state.TvmSignatureCheck
import org.usvm.machine.state.addInt
import org.usvm.machine.state.checkOutOfRange
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.consumeGas
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.slicePreloadDataBits
import org.usvm.machine.state.takeLastIntOrThrowTypeError
import org.usvm.machine.state.takeLastRef
import org.usvm.machine.state.takeLastSlice
import org.usvm.machine.state.unsignedIntegerFitsBits
import org.usvm.machine.types.TvmBuilderType
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmRealReferenceType
import org.usvm.machine.types.TvmSliceType

class TvmCryptoInterpreter(
    private val ctx: TvmContext,
) {
    fun visitCryptoStmt(
        scope: TvmStepScopeManager,
        stmt: TvmAppCryptoInst,
    ) {
        when (stmt) {
            is TvmAppCryptoHashsuInst -> visitSingleHashInst(scope, stmt, operandType = TvmSliceType)
            is TvmAppCryptoHashcuInst -> visitSingleHashInst(scope, stmt, operandType = TvmCellType)
            is TvmAppCryptoChksignuInst -> visitCheckSignatureInst(scope, stmt)
            is TvmAppCryptoHashextSha256Inst -> visitHashExtSha256Inst(scope, stmt)
            else -> TODO("$stmt")
        }
    }

    private fun visitSingleHashInst(
        scope: TvmStepScopeManager,
        stmt: TvmAppCryptoInst,
        operandType: TvmRealReferenceType,
    ) {
        require(operandType != TvmBuilderType) {
            "A single hash function for builders does not exist"
        }

        scope.consumeDefaultGas(stmt)

        scope.calcOnState {
            val value =
                takeLastRef(operandType)
                    ?: return@calcOnState scope.calcOnState(ctx.throwTypeCheckError)

            val hash =
                addressToHash[value] ?: run {
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

    private fun visitCheckSignatureInst(
        scope: TvmStepScopeManager,
        stmt: TvmAppCryptoChksignuInst,
    ) = with(ctx) {
        scope.consumeDefaultGas(stmt)

        val key =
            scope.takeLastIntOrThrowTypeError()
                ?: return@with
        val signature =
            scope.calcOnState { takeLastSlice() }
                ?: return scope.doWithState(throwTypeCheckError)
        val hash =
            scope.takeLastIntOrThrowTypeError()
                ?: return@with

        // Check that signature is correct - it contains at least 512 bits
        val signatureBits =
            scope.slicePreloadDataBits(signature, bits = 512)
                ?: return@with

        val intArgumentBits = 256u

        checkOutOfRange(
            mkAnd(
                unsignedIntegerFitsBits(key, intArgumentBits),
                unsignedIntegerFitsBits(hash, intArgumentBits)
            ),
            scope
        ) ?: return@with

        val condition = scope.calcOnState { makeSymbolicPrimitive(ctx.boolSort) }
        scope.fork(
            condition,
            falseStateIsExceptional = false,
            blockOnFalseState = {
                signatureChecks = signatureChecks.add(TvmSignatureCheck(hash, signatureBits, key, checkPassed = false))

                stack.addInt(falseValue)
                newStmt(stmt.nextStmt())
            }
        ) ?: run {
            logger.warn { "Cannot fork on dummy constraint" }
            return
        }

        scope.doWithState {
            signatureChecks = signatureChecks.add(TvmSignatureCheck(hash, signatureBits, key, checkPassed = true))

            stack.addInt(trueValue)
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitHashExtSha256Inst(
        scope: TvmStepScopeManager,
        stmt: TvmAppCryptoHashextSha256Inst,
    ) = with(ctx) {
        // TODO fix gas
        scope.doWithState { consumeGas(256) }

        val refsToTake =
            scope.takeLastIntOrThrowTypeError()?.intValue()
                ?: return@with

        scope.calcOnState {
            // TODO correct implementation
            repeat(refsToTake) { stack.takeLastEntry() }

            val hash = makeSymbolicPrimitive(ctx.int257sort)
            // Hash is a 256-bit unsigned integer
            scope.assert(
                ctx.unsignedIntegerFitsBits(hash, 256u),
                unsatBlock = { error("Cannot make hash fit in 256 bits") }
            ) ?: return@calcOnState

            stack.addInt(hash)
            newStmt(stmt.nextStmt())
        }
    }
}
