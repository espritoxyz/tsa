import "./messages";
import "./constants";

asm fun myCode(): Cell { MYCODE }

//This contract also has Ownable functionality, but it is implemented without Ownable trait
//to match refference implementation in terms of exit codes.
contract JettonWallet with WalletExitcodes, GasConstants {
    balance: Int as coins;
    owner: Address;
    master: Address;
    const minTonsForStorage: Int = ton("0.015"); // 0.01 TON in original funC implementation. Increased as we have approx. x2 code size
    const gasConsumption: Int = ton("0.015"); // 0.015 TON in original funC implementation.
    // According to tests, 23k gas is maximum gas consuption in transfer. 23k gas is 0.0092 TON
    // More precisely, max gas I could get is 22725
    init(owner: Address, master: Address) {
        self.balance = 0;
        self.owner = owner;
        self.master = master;
    }


    receive(msg: TokenTransfer) {
        nativeThrowUnless(self.IncorrectSender, sender() == self.owner);

        let totalFees: Int = (2 * context().readForwardFee() + 2 * self.gasConsumption) + self.minTonsForStorage + msg.forward_ton_amount;

        //Context() returns Structure with info about incoming message
        nativeThrowUnless(self.UnsufficientAmountOfTonAttached, context().value > totalFees);

        self.balance -= msg.amount;

        //coins type is unsigned type, so
        //self.balance is unsigned when storing and loading (msg.amount is unsigned too),
        //however when doing calculations it's value may be negative, so the check is correct
        nativeThrowUnless(self.IncorrectBalanceAfrerSend, self.balance >= 0);


        //We shouldn't send transfers to masterchain due to higher gas price
        nativeThrowUnless(self.InvalidDestinationWorkchain, parseStdAddress(msg.destination.asSlice()).workchain == 0);

        let init: StateInit = initOf JettonWallet(msg.destination, self.master);
        let wallet_address: Address = contractAddress(init);

        send(SendParameters{
        to: wallet_address,
        value: 0,
        mode: SendRemainingValue,
        bounce: true,
        body: TokenTransferInternal {
        query_id: msg.query_id,
        amount: msg.amount,
        from: self.owner,
        response_destination: msg.response_destination,
        forward_ton_amount: msg.forward_ton_amount,
        forward_payload: msg.forward_payload
        }.toCell(),
        code: init.code,
        data: init.data
        });
    }

    receive(msg: TokenTransferInternal) {
        // This message should come only from master, or from other JettonWallet
        if (sender() != self.master) {
            let init: StateInit = initOf JettonWallet(msg.from, self.master);
            nativeThrowUnless(self.IncorrectSenderInternal, contractAddress(init) == sender());
        }
        // Update balance
        self.balance += msg.amount;

        //Commented require() here because self.balance and msg.amount are coins, so they are unsigned
        //require(self.balance >= 0, "Invalid balance");
        // Get value for gas

        let ctx: Context = context(); //Context of current message
        let msgValue: Int = ctx.value;
        let tonBalanceBeforeMsg = myBalance() - msgValue;
        let storageFee = self.minTonsForStorage - min(tonBalanceBeforeMsg, self.minTonsForStorage);
        msgValue -= (storageFee + self.gasConsumption);

        let fwd_fee: Int = ctx.readForwardFee();


        if (msg.forward_ton_amount > 0) {
            msgValue = ((msgValue - msg.forward_ton_amount) - fwd_fee);
            send(SendParameters{
            to: self.owner,
            value: msg.forward_ton_amount,
            mode: SendPayGasSeparately,
            bounce: false,
            body: TokenNotification{ // 0x7362d09c -- Remind the new Owner
            query_id: msg.query_id,
            amount: msg.amount,
            from: msg.from,
            forward_payload: msg.forward_payload
            }.toCell()
            });
        }
        // 0xd53276db -- Cashback to the original Sender
        if (msg.response_destination != null && msgValue > 0) {
            send(SendParameters{
            to: msg.response_destination!!,
            value: msgValue,
            mode: SendIgnoreErrors, // Jetton transfer is already succeeded
            //In official funC implementation it is SendIgnoreErrors
            bounce: false,
            body: TokenExcesses{
            query_id: msg.query_id
            }.toCell()
            });
        }
    }

    receive(msg: TokenBurn) {
        nativeThrowUnless(self.IncorrectSender, sender() == self.owner);

        let ctx: Context = context();
        self.balance -= msg.amount; // Update balance
        nativeThrowUnless(self.IncorrectBalanceAfrerSend, self.balance >= 0);

        // This is minimal possible amount of TONs for attached.
        nativeThrowUnless(self.UnsufficientAmountOfTonForBurn, ctx.value > self.gasForBurn);
        // Burn tokens
        send(SendParameters{
        to: self.master,
        value: 0,
        mode: SendRemainingValue,
        bounce: true,
        body: TokenBurnNotification{
        query_id: msg.query_id,
        amount: msg.amount,
        sender: self.owner,
        response_destination: msg.response_destination
        }.toCell()
        });
    }

    bounced(msg: bounced<TokenTransferInternal>){
        self.balance += msg.amount;
    }

    bounced(msg: bounced<TokenBurnNotification>){
        self.balance += msg.amount;
    }

    get fun get_wallet_data(): JettonWalletData {
        return JettonWalletData{
        balance: self.balance,
        owner: self.owner,
        master: self.master,
        code: myCode() //may be replaced with "initOf JettonDefaultWallet(self.owner, self.master).code"
        };
    }
}