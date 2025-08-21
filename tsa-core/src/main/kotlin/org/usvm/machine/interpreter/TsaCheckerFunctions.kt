package org.usvm.machine.interpreter

const val FORBID_FAILURES_METHOD_ID = 1
const val ALLOW_FAILURES_METHOD_ID = 2
const val ASSERT_METHOD_ID = 3
const val ASSERT_NOT_METHOD_ID = 4
const val FETCH_VALUE_ID = 5
const val SEND_INTERNAL_MESSAGE_ID = 6
const val GET_C4_METHOD_ID = 7

const val ON_INTERNAL_MESSAGE_METHOD_ID = 65621

const val MK_SYMBOLIC_INT_METHOD_ID = 100

fun extractStackOperationsFromMethodId(methodId: Int): SimpleStackOperations? {
    val firstDigit = methodId / 10000
    if (firstDigit != 1) {
        return null
    }
    val rest = methodId % 10000
    val putOnNewStack = rest % 100
    val takeFromNewStack = rest / 100
    return SimpleStackOperations(putOnNewStack, takeFromNewStack)
}

sealed interface StackOperations

data class SimpleStackOperations(
    val putOnNewStack: Int,
    val takeFromNewStack: Int,
) : StackOperations

data class NewRecvInternalInput(val inputId: Int) : StackOperations
