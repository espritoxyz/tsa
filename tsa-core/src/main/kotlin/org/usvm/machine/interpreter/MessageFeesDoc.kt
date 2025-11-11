package org.usvm.machine.interpreter

// this file serves to be a documentation of the way message fees are calculated

/**
 * Represents a dummy message that was added to the action list by
 * SENDMSG or a similar instruction
 */
@Suppress("unused")
data object MessageInAction {
    fun getFlag(): Boolean = TODO()

    fun calculateFees(): Int = TODO()

    val originallyAttachedValue: Int = TODO()
}

/**
 * Represents a dummy contract state.
 * The message handling here is based on the flow in `Transaction::try_action_send_msg` defined in
 * `crypto/block/transaction.cpp` file relative to the root of the TON monorepo, tag `v2025.7`.
 *
 * In docs, originally = from the source code of `Transaction::try_action_send_msg`
 */
@Suppress("unused")
class ContractState {
    /**
     * originally, `ap.remaining_balance`
     */
    var balance: Int = TODO()

    /**
     * originally, `this->msg_balance_remaining`
     */
    var inboundMessageValueRemaining: Int = TODO()

    /**
     * originally, `this->compute_phase->gas_fees`
     */
    val computeFees: Int = TODO()

    fun throwOutOfBalance(): Nothing = TODO()

    fun addActualMessage(value: Int) {
        // ...
    }

    /**
     * The message from derived from the sources of TVM.
     * Some variables have documentation of their name in the source of TVM.
     */
    fun processFeesOfMessage(messageInAction: MessageInAction) {
        val sendRemainingValue: Boolean = messageInAction.getFlag()
        val sendRemainingBalance: Boolean = messageInAction.getFlag()
        var payFeesSeparately: Boolean = messageInAction.getFlag()

        /**
         * originally, `fees_total (= fwd_fee + ihr_fee)`
         */
        val msgFees = messageInAction.calculateFees()

        /** originally, `req` */
        var constructedMessageValue = messageInAction.originallyAttachedValue
        payFeesSeparately = payFeesSeparately && !sendRemainingBalance

        if (sendRemainingBalance) {
            inboundMessageValueRemaining = 0
            constructedMessageValue = balance
        } else if (sendRemainingValue) {
            inboundMessageValueRemaining = 0
            if (constructedMessageValue >= computeFees) {
                constructedMessageValue -= computeFees
            } else {
                throwOutOfBalance()
            }
        } else {
            // do nothing
        }
        /** originally, `reqBrutto` */
        var messageSendCost: Int

        if (payFeesSeparately) {
            messageSendCost = constructedMessageValue + msgFees
        } else {
            if (constructedMessageValue < msgFees) {
                throwOutOfBalance()
            } else {
                messageSendCost = constructedMessageValue
                constructedMessageValue = constructedMessageValue - msgFees
            }
        }

        if (balance >= messageSendCost) {
            balance -= messageSendCost
            addActualMessage(constructedMessageValue)
        } else {
            throwOutOfBalance()
        }
    }
}
