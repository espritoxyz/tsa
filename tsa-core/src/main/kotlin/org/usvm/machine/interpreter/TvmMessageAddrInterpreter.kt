package org.usvm.machine.interpreter

import org.ton.bytecode.TvmAppAddrInst
import org.ton.bytecode.TvmAppAddrLdmsgaddrInst
import org.ton.bytecode.TvmAppAddrRewritestdaddrInst
import org.usvm.logger
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.ADDRESS_BITS
import org.usvm.machine.TvmContext.Companion.STD_WORKCHAIN_BITS
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.addInt
import org.usvm.machine.state.addOnStack
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.getSliceRemainingBitsCount
import org.usvm.machine.state.getSliceRemainingRefsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.sliceCopy
import org.usvm.machine.state.sliceLoadAddrTlb
import org.usvm.machine.state.sliceMoveDataPtr
import org.usvm.machine.state.slicePreloadDataBits
import org.usvm.machine.state.takeLastSlice
import org.usvm.machine.types.TvmSliceType

class TvmMessageAddrInterpreter(
    private val ctx: TvmContext,
) {
    fun visitAddrInst(scope: TvmStepScopeManager, stmt: TvmAppAddrInst) {
        scope.consumeDefaultGas(stmt)

        when (stmt) {
            is TvmAppAddrLdmsgaddrInst -> visitLoadMessageAddrInst(scope, stmt)
            is TvmAppAddrRewritestdaddrInst -> visitParseStdAddr(scope, stmt)
            else -> TODO("$stmt")
        }
    }

    private fun visitLoadMessageAddrInst(scope: TvmStepScopeManager, stmt: TvmAppAddrLdmsgaddrInst) = with(ctx) {
        val slice = scope.calcOnState { stack.takeLastSlice() }
            ?: return scope.doWithState(throwTypeCheckError)

        val updatedSlice = scope.calcOnState {
            memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) }
        }

        sliceLoadAddrTlb(scope, slice, updatedSlice) { value ->
            doWithState {
                addOnStack(value, TvmSliceType)
                addOnStack(updatedSlice, TvmSliceType)

                newStmt(stmt.nextStmt())
            }
        }
    }

    private fun visitParseStdAddr(scope: TvmStepScopeManager, inst: TvmAppAddrRewritestdaddrInst) {
        scope.doWithStateCtx {
            // TODO support var address

            val slice = stack.takeLastSlice()
            if (slice == null) {
                throwTypeCheckError(this)
                return@doWithStateCtx
            }

            val copySlice = memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) }
            val addrConstructor = scope.slicePreloadDataBits(copySlice, bits = 2)
                ?: TODO("Deal with incorrect address")
            sliceMoveDataPtr(copySlice, bits = 2)

            scope.assert(
                addrConstructor eq mkBv(value = 2, sizeBits = 2u),
                unsatBlock = {
                    // TODO Deal with non addr_std
                    logger.debug { "Non-std addr found, dropping the state" }
                }
            ) ?: return@doWithStateCtx

            val anycastBit = scope.slicePreloadDataBits(copySlice, bits = 1)
                ?: TODO("Deal with incorrect address")
            sliceMoveDataPtr(copySlice, bits = 1)
            scope.assert(
                anycastBit eq zeroBit,
                unsatBlock = {
                    // TODO Deal with anycast
                    logger.debug { "Cannot assume no anycast" }
                }
            ) ?: return@doWithStateCtx

            val workchain = scope.slicePreloadDataBits(copySlice, bits = STD_WORKCHAIN_BITS)?.signedExtendToInteger()
                ?: TODO("Deal with incorrect address")
            sliceMoveDataPtr(copySlice, bits = STD_WORKCHAIN_BITS)

            val workchainValueConstraint = (workchain eq baseChain) or (workchain eq masterchain)
            scope.assert(
                workchainValueConstraint,
                unsatBlock = {
                    error("Cannot assume valid workchain value")
                }
            ) ?: return@doWithStateCtx

            val address = scope.slicePreloadDataBits(copySlice, bits = ADDRESS_BITS)
                ?: TODO("Deal with incorrect address")
            sliceMoveDataPtr(copySlice, bits = ADDRESS_BITS)

            val bitsLeft = getSliceRemainingBitsCount(copySlice)
            val refsLeft = getSliceRemainingRefsCount(copySlice)
            val emptySuffixConstraint = (bitsLeft eq zeroSizeExpr) and (refsLeft eq zeroSizeExpr)
            scope.fork(
                emptySuffixConstraint,
                falseStateIsExceptional = true,
                // TODO set cell deserialization failure
                blockOnFalseState = throwUnknownCellUnderflowError
            ) ?: return@doWithStateCtx

            stack.addInt(workchain)
            stack.addInt(address.unsignedExtendToInteger())

            newStmt(inst.nextStmt())
        }
    }
}