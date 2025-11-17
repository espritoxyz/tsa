package org.usvm.machine.interpreter

const val FORBID_FAILURES_METHOD_ID = 1
const val ALLOW_FAILURES_METHOD_ID = 2
const val ASSERT_METHOD_ID = 3
const val ASSERT_NOT_METHOD_ID = 4
const val FETCH_VALUE_ID = 5
const val SEND_INTERNAL_MESSAGE_ID = 6
const val GET_C4_METHOD_ID = 7
const val SEND_EXTERNAL_MESSAGE_ID = 8
const val GET_BALANCE_ID = 9

/**
 * Were calculated using python script:
 * ```
 * int(pytoniq_core.crc.crc16(b"<method_name>").hex(), base=16) | 65536
 * ```
 * Represent the method id of the same-named method in FunC.
 * See [TON docs](https://docs.ton.org/v3/documentation/smart-contracts/func/docs/functions)
 * This method's name is "on_internal_message_send"
 */
const val ON_INTERNAL_MESSAGE_METHOD_ID = 65621

/**
 * This method's name is "on_external_message_send".
 */
const val ON_EXTERNAL_MESSAGE_METHOD_ID = 97889

/**
 * This method's name is "on_out_message"
 */
const val ON_OUT_MESSAGE_METHOD_ID = 71561

/**
 * This method's name is "on_compute_phase_exit"
 */
const val ON_COMPUTE_PHASE_EXIT_METHOD_ID = 69471

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

data class NewReceiverInput(
    val inputId: Int,
    val type: ReceiverType,
) : StackOperations

enum class ReceiverType {
    Internal,
    External,
}
