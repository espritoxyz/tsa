import "./messages";

contract AmmPool() {
    receive(msg: SwapIn) {
        let inVault = sender();
        let outVault = inVault; // send back
        message(MessageParameters {
            value: 0,
            mode: SendRemainingValue,
            to: outVault,
            body: PayoutFromPool {
                otherVault: inVault,
                amount: msg.amount,
                receiver: msg.receiver,
                payloadToForward: msg.params.payloadOnSuccess,
            }.toCell(),
        });
    }
}
