package org.ton.bytecode

import kotlinx.serialization.Serializable
import org.usvm.machine.interpreter.TsaCheckerFunctionsInterpreter
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmActionPhase
import org.usvm.machine.state.TvmBouncePhase
import org.usvm.machine.state.TvmComputePhase
import org.usvm.machine.state.TvmResult
import org.usvm.machine.state.messages.MessageAsStackArguments

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

@Serializable
data class TsaArtificialActionPhaseInst(
    val computePhaseResult: TvmResult.TvmTerminalResult,
    override val location: TvmInstLocation,
) : TsaArtificialInst {
    override val mnemonic: String get() = "artificial_action_phase"

    init {
        checkLocationInitialized()
        check(computePhaseResult.phase is TvmComputePhase) {
            "Unexpected computePhaseResult in TsaArtificialActionPhaseInst: $computePhaseResult"
        }
    }
}

/**
 *  This instruction is automatically inserted after the action phase
 *  and is used to call the `on_out_message` handler in checker with
 *  returning back to process the contract exit.
 */
data class TsaArtificialOnOutMessageHandlerCallInst(
    val computePhaseResult: TvmResult.TvmTerminalResult,
    val actionPhaseResult: TvmResult.TvmTerminalResult?,
    override val location: TvmInstLocation,
    val sentMessages: List<SentMessage>,
) : TsaArtificialInst {
    override val mnemonic: String get() = "on_out_message_hack"

    init {
        checkLocationInitialized()
        check(computePhaseResult.phase is TvmComputePhase) {
            "Unexpected computePhaseResult in TsaArtificialOnOutMessageHandlerCallInst: $computePhaseResult"
        }
        check(actionPhaseResult?.phase is TvmActionPhase?) {
            "Unexpected actionPhaseResult in TsaArtificialOnOutMessageHandlerCallInst: $actionPhaseResult"
        }
    }

    data class SentMessage(
        val message: MessageAsStackArguments,
        val receiver: ContractId?,
    )
}

data class TsaArtificialOnComputePhaseExitInst(
    val computePhaseResult: TvmResult.TvmTerminalResult,
    override val location: TvmInstLocation,
) : TsaArtificialInst {
    override val mnemonic: String get() = "on_compute_phase_exit"

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
