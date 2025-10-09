package org.ton.bytecode

const val TAG_PARAMETER_IDX: Int = 0
const val ACTIONS_PARAMETER_IDX: Int = 1
const val MSGS_SENT_PARAMETER_IDX: Int = 2
const val TIME_PARAMETER_IDX: Int = 3
const val BLOCK_TIME_PARAMETER_IDX: Int = 4
const val TRANSACTION_TIME_PARAMETER_IDX: Int = 5
const val SEED_PARAMETER_IDX: Int = 6
const val BALANCE_PARAMETER_IDX: Int = 7
const val ADDRESS_PARAMETER_IDX: Int = 8
const val CONFIG_PARAMETER_IDX: Int = 9
const val CODE_PARAMETER_IDX: Int = 10
const val INBOUND_MESSAGE_VALUE_PARAMETER_IDX: Int = 11
const val STORAGE_FEES_PARAMETER_IDX: Int = 12
const val PREV_BLOCK_PARAMETER_IDX: Int = 13
const val DUE_PAYMENT_IDX = 15

fun List<TvmInst>.flattenStatements(): List<TvmInst> {
    val statements = mutableListOf<TvmInst>()
    val stack = mutableListOf(this)

    while (stack.isNotEmpty()) {
        val instList = stack.removeLast()

        instList.forEach { stmt ->
            if (stmt is TvmArtificialInst) {
                return@forEach
            }

            statements.add(stmt)
            extractInstLists(stmt).forEach(stack::add)
        }
    }

    return statements
}

private fun extractInstLists(stmt: TvmInst): Sequence<List<TvmInst>> =
    when (stmt) {
        is TvmContOperand1Inst -> sequenceOf(stmt.c.list)
        is TvmContOperand2Inst -> sequenceOf(stmt.c1.list, stmt.c2.list)
        else -> emptySequence()
    }
