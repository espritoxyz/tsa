package org.ton.bytecode

import kotlinx.serialization.Serializable
import org.usvm.machine.interpreter.TsaCheckerFunctionsInterpreter
import org.usvm.machine.state.ContractId
import org.usvm.machine.state.TvmMethodResult
import org.usvm.machine.state.messages.OutMessage

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
    val computePhaseResult: TvmMethodResult,
    override val location: TvmInstLocation,
) : TsaArtificialInst {
    override val mnemonic: String get() = "artificial_action_phase"

    init {
        checkLocationInitialized()
    }
}

/**
 *  This instruction is automatically inserted after the action phase
 *  and is used to call the `on_out_message` handler in checker with
 *  returning back to process the contract exit.
 */
data class TsaArtificialOnOutMessageHandlerCallInst(
    val computePhaseResult: TvmMethodResult,
    override val location: TvmInstLocation,
    val sentMessages: List<SentMessage>,
) : TsaArtificialInst {
    override val mnemonic: String get() = "on_out_message_hack"

    init {
        checkLocationInitialized()
    }

    data class SentMessage(
        val message: OutMessage,
        val receiver: ContractId?,
    )
}

@Serializable
data class TsaArtificialBouncePhaseInst(
    val computePhaseResult: TvmMethodResult,
    override val location: TvmInstLocation,
) : TsaArtificialInst {
    override val mnemonic: String get() = "artificial_bounce_phase"

    init {
        checkLocationInitialized()
    }
}

@Serializable
data class TsaArtificialExitInst(
    val result: TvmMethodResult,
    override val location: TvmInstLocation,
) : TsaArtificialInst {
    override val mnemonic: String get() = "artificial_exit"

    init {
        checkLocationInitialized()
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

class TsaArtificialCheckerReturn(
    override val location: TvmInstLocation,
    val checkerMemorySavelist: TsaCheckerFunctionsInterpreter.CheckerMemorySavelist,
) : TsaArtificialInst {
    override val mnemonic: String get() = "artificial_checker_return"
}
