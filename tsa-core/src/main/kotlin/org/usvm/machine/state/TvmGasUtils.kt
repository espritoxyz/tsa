package org.usvm.machine.state

import org.ton.bytecode.TvmComplexGas
import org.ton.bytecode.TvmFixedGas
import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmSimpleGas
import org.usvm.UBv32Sort
import org.usvm.UExpr
import org.usvm.machine.TvmStepScopeManager

const val IMPLICIT_EXCEPTION_THROW_GAS = 50
const val SIMPLE_INSTRUCTION_BASE_GAS = 10
const val SIMPLE_INSTRUCTION_BIT_GAS = 1

const val SIMPLE_INSTRUCTION_BIT_SIZE = 16 // todo: check for instructions with simple gas and another bit size
const val SIMPLE_GAS_USAGE =
    SIMPLE_INSTRUCTION_BASE_GAS + SIMPLE_INSTRUCTION_BIT_SIZE * SIMPLE_INSTRUCTION_BIT_GAS // 26

fun TvmStepScopeManager.consumeDefaultGas(stmt: TvmInst) =
    doWithState {
        consumeDefaultGas(stmt)
    }

fun TvmStepScopeManager.consumeConstantGas(value: Int) =
    doWithState {
        consumeGas(value)
    }

fun TvmState.consumeDefaultGas(stmt: TvmInst) =
    when (val gas = stmt.gasConsumption) {
        is TvmFixedGas -> consumeGas(gas.value)
        TvmSimpleGas -> consumeGas(SIMPLE_GAS_USAGE)
        is TvmComplexGas -> error("$stmt has complex Gas usage")
    }

fun TvmState.consumeGas(stmtGasUsage: Int) = consumeGas(ctx.mkBv(stmtGasUsage))

fun TvmState.consumeGas(stmtGasUsage: UExpr<UBv32Sort>) {
    gasUsageHistory = gasUsageHistory.add(currentContract to stmtGasUsage)
}
