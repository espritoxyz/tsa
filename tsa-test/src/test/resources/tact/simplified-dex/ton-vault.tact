import "./messages";
import "./amm-pool";

contract TonVault() {
    receive(msg: SwapRequestTon) {
        let receiver = msg.action.receiver != null ? msg.action.receiver!! : sender();
        message(MessageParameters {
            to: msg.action.pool,
            value: 0,
            mode: SendRemainingBalance,
            bounce: false,
            body: SwapIn {
                receiver,
                amount: msg.amount,
                params: msg.action.params,
            }.toCell(),
        });
    }

    receive(msg: PayoutFromPool) {
        message(MessageParameters {
            to: msg.receiver,
            value: msg.amount,
            mode: SendRemainingValue,
            bounce: false,
            body: msg.payloadToForward,
        });
    }
}
