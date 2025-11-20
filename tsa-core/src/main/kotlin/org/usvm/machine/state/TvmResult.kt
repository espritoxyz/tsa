package org.usvm.machine.state

import kotlinx.serialization.Serializable
import org.ton.TlbBuiltinLabel
import org.ton.bytecode.TvmInst
import org.usvm.PathNode
import org.usvm.UBv32Sort
import org.usvm.UExpr
import org.usvm.machine.state.TvmResult.TvmAbstractSoftFailure
import org.usvm.machine.state.TvmResult.TvmErrorExit
import org.usvm.machine.state.TvmResult.TvmSuccessfulExit
import org.usvm.machine.types.TvmCellDataTypeRead
import org.usvm.machine.types.TvmStructuralExit

/**
 * Represents a result of a method invocation.
 */
sealed interface TvmResult {
    sealed interface TvmTerminalResult : TvmResult {
        val phase: TvmPhase
    }

    /**
     * No call was performed.
     */
    data object NoCall : TvmResult

    /**
     * A method successfully returned.
     */
    data class TvmComputePhaseSuccess(
        val exit: TvmSuccessfulExit,
        val stack: TvmStack,
    ) : TvmTerminalResult {
        override val phase: TvmPhase = TvmComputePhase
    }

    data class TvmActionPhaseSuccess(
        val computePhaseResult: TvmTerminalResult,
    ) : TvmTerminalResult {
        override val phase: TvmPhase = TvmActionPhase(computePhaseResult)
    }

    /**
     * A method exited with non-successful exit code.
     */
    data class TvmFailure(
        val exit: TvmErrorExit,
        val type: TvmFailureType,
        override val phase: TvmPhase,
        val pathNodeAtFailurePoint: PathNode<TvmInst>,
    ) : TvmTerminalResult {
        override fun toString(): String =
            if (type == TvmFailureType.UnknownError) {
                "TvmFailure(exit=$exit, phase=$phase)"
            } else {
                "TvmFailure(exit=$exit, type=$type, phase=$phase)"
            }
    }

    @Serializable
    sealed interface TvmExit {
        // Could be negative for custom exit codes
        val exitCode: Int
    }

    interface TvmSuccessfulExit : TvmExit

    interface TvmErrorExit : TvmExit {
        val ruleName: String
    }

    sealed interface TvmAbstractSoftFailure : TvmTerminalResult {
        override val phase: TvmPhase
    }

    data class TvmSoftFailure(
        val exit: TvmSoftFailureExit,
        override val phase: TvmPhase,
    ) : TvmAbstractSoftFailure

    sealed interface TvmSoftFailureExit {
        val ruleId: String
    }
}

data class TvmStructuralError(
    val exit: TvmStructuralExit<TvmCellDataTypeRead<*>, TlbBuiltinLabel>,
    override val phase: TvmPhase,
) : TvmAbstractSoftFailure

data object TvmUsageOfAnycastAddress : TvmResult.TvmSoftFailureExit {
    override val ruleId = "anycast-address-usage"
}

data object TvmUsageOfVarAddress : TvmResult.TvmSoftFailureExit {
    override val ruleId = "var-address-usage"
}

data object TvmDictOperationOnDataCell : TvmResult.TvmSoftFailureExit {
    override val ruleId = "dict-operation-on-data-cell"
}

data object TvmDataCellOperationOnDict : TvmResult.TvmSoftFailureExit {
    override val ruleId = "data-cell-operation-on-dict"
}

data class TvmDoubleSendRemainingValue(
    val contractId: ContractId,
) : TvmResult.TvmSoftFailureExit {
    override val ruleId = "double-send-remaining-value"
}

object TvmNormalExit : TvmSuccessfulExit {
    override val exitCode: Int
        get() = 0

    override fun toString(): String = "Successful termination, exit code: $exitCode"
}

object TvmAlternativeExit : TvmSuccessfulExit {
    override val exitCode: Int
        get() = 1

    override fun toString(): String = "Successful termination, exit code: $exitCode"
}

/**
 * In some cases, TvmExit is not enough to identify the type of the failure.
 * For example, cellUnderflow can occur due to real programmer's error, or
 * due to the fact that we generated input values with bad structure.
 * TvmFailureType is used to distinguish these situations.
 */
@Serializable
enum class TvmFailureType {
    /**
     * Error due to bad input object structure.
     *
     * Example: input_slice~load_bits(128), when len(input_slice) < 128
     */
    StructuralError,

    /**
     * Real programmer's error.
     *
     * Example: s = "a"; s~load_bits(128);
     */
    RealError,

    /**
     * Extra failure information couldn't be inferred.
     */
    UnknownError,
}

@Serializable
object TvmStackUnderflowError : TvmErrorExit {
    override val exitCode: Int = 2
    override val ruleName: String = "stack-underflow"

    override fun toString(): String = "TVM stack underflow, exit code: $exitCode"
}

@Serializable
object TvmStackOverflowError : TvmErrorExit {
    override val exitCode: Int = 3
    override val ruleName: String = "stack-overflow"

    override fun toString(): String = "TVM stack overflow, exit code: $exitCode"
}

// TODO standard exit code should be placed in codepage 0?
// TODO add integer underflow?
@Serializable
object TvmIntegerOverflowError : TvmErrorExit {
    override val exitCode: Int = 4
    override val ruleName: String = "integer-overflow"

    override fun toString(): String = "TVM integer overflow, exit code: $exitCode"
}

@Serializable
object TvmIntegerOutOfRangeError : TvmErrorExit {
    override val exitCode: Int = 5
    override val ruleName: String = "integer-out-of-range"

    override fun toString(): String =
        "TVM integer out of expected range, exit code: $exitCode" // TODO add expected range to the message?
}

// TODO add expected type
@Serializable
object TvmTypeCheckError : TvmErrorExit {
    override val exitCode: Int = 7
    override val ruleName: String = "wrong-type"

    override fun toString(): String = "TVM type check error, exit code: $exitCode"
}

@Serializable
object TvmCellOverflowError : TvmErrorExit {
    override val exitCode: Int = 8
    override val ruleName: String = "cell-overflow"

    override fun toString(): String = "TVM cell overflow, exit code: $exitCode"
}

@Serializable
object TvmCellUnderflowError : TvmErrorExit {
    override val exitCode: Int = 9
    override val ruleName: String = "cell-underflow"

    override fun toString(): String = "TVM cell underflow, exit code: $exitCode"
}

@Serializable
object TvmDictError : TvmErrorExit {
    override val exitCode: Int = 10
    override val ruleName: String = "dict-error"

    override fun toString(): String = "TVM dictionary error, exit code: $exitCode"
}

data class TvmOutOfGas(
    val consumedGas: UExpr<UBv32Sort>,
    val gasLimit: UExpr<UBv32Sort>,
) : TvmErrorExit {
    override val exitCode: Int = 13
    override val ruleName: String = "out-of-gas"

    override fun toString(): String =
        "TVM out of gas error (exit code: $exitCode): gas consumed: $consumedGas, limit: $gasLimit"
}

data class InsufficientFunds(
    val contractId: ContractId,
) : TvmErrorExit {
    override val exitCode: Int = 37
    override val ruleName: String = "insufficient-funds"

    override fun toString(): String = "TVM insufficient funds while processing messages sent by contract $contractId"
}

@Serializable
data class TvmUserDefinedFailure(
    override val exitCode: Int,
) : TvmErrorExit {
    override val ruleName: String = "user-defined-error"

    override fun toString(): String = "TVM user defined error with exit code $exitCode"
}

fun TvmResult.isExceptional(): Boolean = this is TvmResult.TvmFailure || this is TvmAbstractSoftFailure
