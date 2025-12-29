package org.ton.bytecode

import kotlinx.serialization.Serializable
import org.ton.DestinationDescription
import org.usvm.machine.interpreter.DispatchedMessage
import org.usvm.machine.interpreter.TsaCheckerFunctionsInterpreter
import org.usvm.machine.interpreter.TvmTransactionInterpreter
import org.usvm.machine.state.TvmActionPhase
import org.usvm.machine.state.TvmBouncePhase
import org.usvm.machine.state.TvmComputePhase
import org.usvm.machine.state.TvmResult
import org.usvm.machine.state.messages.MessageActionParseResult
import org.usvm.machine.types.SliceRef

/**
 * - In one way or another the compute phase ends
 * - [TsaArtificialOnComputePhaseExitInst]
 * - [TsaArtificialActionPhaseStartInst]
 * - [TsaArtificialActionParseInst] (one for each action in an action list and one for an empty list)
 * - [TsaArtificialHandleMessagesCostInst]
 * - [TsaArtificialOnOutMessageHandlerCallInst]
 * - [TsaArtificialBouncePhaseInst]
 * - [TsaArtificialExitInst]
 */
@Suppress("Unused")
private object ArtificialInstructionOrder

sealed interface TsaArtificialInst : TvmArtificialInst

/**
 * Instruction that marks the beginning of a loop iteration
 */
@Serializable
data class TsaArtificialLoopEntranceInst(
    val id: UInt,
    override val location: TvmInstLocation,
) : TsaArtificialInst {
    override val mnemonic: String get() = "artificial_loop_entrance"

    init {
        checkLocationInitialized()
    }
}

@Serializable
data class TsaArtificialImplicitRetInst(
    override val location: TvmInstLocation,
) : TsaArtificialInst {
    override val mnemonic: String get() = "implicit RET"
    override val gasConsumption get() = TvmFixedGas(value = 5)

    init {
        checkLocationInitialized()
    }
}

/**
 * Marks the start of an action phase.
 * Is followed by [TsaArtificialActionParseInst]
 */
@Serializable
data class TsaArtificialActionPhaseStartInst(
    val computePhaseResult: TvmResult.TvmTerminalResult,
    override val location: TvmInstLocation,
) : TsaArtificialInst {
    override val mnemonic: String get() = "artificial_action_phase_start"

    init {
        checkLocationInitialized()
        check(computePhaseResult.phase is TvmComputePhase) {
            "Unexpected computePhaseResult in TsaArtificialActionPhaseInst: $computePhaseResult"
        }
    }
}

/**
 * Handles the parsing and preprocessing of actions from the action list (stored in C5 register).
 * Works with a single action ([yetUnparsedActions]`.first()`) per instruction.
 * Is followed by the same instruction with updated fields or [TsaArtificialHandleMessagesCostInst] when the parsing
 * is complete.
 */
data class TsaArtificialActionParseInst(
    val computePhaseResult: TvmResult.TvmTerminalResult,
    override val location: TvmInstLocation,
    val yetUnparsedActions: List<SliceRef>,
    val parsedAndPreprocessedActions: List<MessageActionParseResult>,
    val destinationResolver: DestinationDescription?,
) : TsaArtificialInst {
    override val mnemonic: String get() = "artificial_action_parse_inst"
}

/**
 * Handles costs of messages and sends the messages into the blockchain, both with respect to the modes the
 * messages were sent with.
 * Is followed by [TsaArtificialOnOutMessageHandlerCallInst].
 */
data class TsaArtificialHandleMessagesCostInst(
    val computePhaseResult: TvmResult.TvmTerminalResult,
    override val location: TvmInstLocation,
    val parsingResult: TvmTransactionInterpreter.ActionsParsingResult,
) : TsaArtificialInst {
    override val mnemonic: String get() = "artificial_action_handle_messages_cost"
}

/**
 *  This instruction is automatically inserted after the action phase
 *  and is used to call the `on_out_message` handler in checker with
 *  returning back to process the contract exit.
 *  Is followed by itsef with updated fields or with [TsaArtificialExitInst] after all the messages are passed to
 *  the handler.
 *  @param messageOrderNumber represents the number of the sent message (specifically, of the [sentMessages]`.first()`)
 *  from the corresponding contract execution
 */
data class TsaArtificialOnOutMessageHandlerCallInst(
    val computePhaseResult: TvmResult.TvmTerminalResult,
    val actionPhaseResult: TvmResult.TvmTerminalResult?,
    override val location: TvmInstLocation,
    val sentMessages: List<DispatchedMessage>,
    val messageOrderNumber: Int,
) : TsaArtificialInst {
    override val mnemonic: String get() = "artificial_on_out_message_bridge"

    init {
        checkLocationInitialized()
        check(computePhaseResult.phase is TvmComputePhase) {
            "Unexpected computePhaseResult in TsaArtificialOnOutMessageHandlerCallInst: $computePhaseResult"
        }
        check(actionPhaseResult?.phase is TvmActionPhase?) {
            "Unexpected actionPhaseResult in TsaArtificialOnOutMessageHandlerCallInst: $actionPhaseResult"
        }
    }
}

data class TsaArtificialOnComputePhaseExitInst(
    val computePhaseResult: TvmResult.TvmTerminalResult,
    override val location: TvmInstLocation,
) : TsaArtificialInst {
    override val mnemonic: String get() = "on_compute_phase_exit_bridge"

    init {
        checkLocationInitialized()
    }
}

@Serializable
data class TsaArtificialBouncePhaseInst(
    val computePhaseResult: TvmResult.TvmTerminalResult,
    val actionPhaseResult: TvmResult.TvmTerminalResult?,
    override val location: TvmInstLocation,
) : TsaArtificialInst {
    override val mnemonic: String get() = "artificial_bounce_phase"

    init {
        checkLocationInitialized()
        check(computePhaseResult.phase is TvmComputePhase) {
            "Unexpected computePhaseResult in TsaArtificialBouncePhaseInst: $computePhaseResult"
        }
        check(actionPhaseResult?.phase is TvmActionPhase?) {
            "Unexpected actionPhaseResult in TsaArtificialBouncePhaseInst: $actionPhaseResult"
        }
    }
}

@Serializable
data class TsaArtificialExitInst(
    val computePhaseResult: TvmResult.TvmTerminalResult,
    val actionPhaseResult: TvmResult.TvmTerminalResult?,
    override val location: TvmInstLocation,
) : TsaArtificialInst {
    override val mnemonic: String get() = "artificial_exit"

    init {
        checkLocationInitialized()
        check(computePhaseResult.phase is TvmComputePhase) {
            "Unexpected computePhaseResult in TsaArtificialExitInst: $computePhaseResult"
        }
        check(actionPhaseResult?.phase is TvmActionPhase? || actionPhaseResult.phase is TvmBouncePhase) {
            "Unexpected actionPhaseResult in TsaArtificialExitInst: $actionPhaseResult"
        }
    }
}

sealed interface TsaArtificialContInst : TsaArtificialInst {
    val cont: TvmContinuation
}

@Serializable
data class TsaArtificialJmpToContInst(
    override val cont: TvmContinuation,
    override val location: TvmInstLocation,
) : TsaArtificialContInst {
    override val mnemonic: String get() = "artificial_jmp_to_$cont"

    init {
        checkLocationInitialized()
    }
}

class TsaArtificialExecuteContInst(
    override val cont: TvmContinuation,
    override val location: TvmInstLocation,
) : TsaArtificialContInst {
    override val mnemonic: String get() = "artificial_execute_$cont"

    init {
        checkLocationInitialized()
    }
}

data class TsaArtificialCheckerReturn(
    override val location: TvmInstLocation,
    val checkerMemorySavelist: TsaCheckerFunctionsInterpreter.CheckerMemorySavelist,
) : TsaArtificialInst {
    override val mnemonic: String get() = "artificial_checker_return"
}

data class TsaArtificialPostprocessInst(
    override val location: TvmInstLocation,
) : TsaArtificialInst {
    override val mnemonic: String get() = "postprocess"
}
