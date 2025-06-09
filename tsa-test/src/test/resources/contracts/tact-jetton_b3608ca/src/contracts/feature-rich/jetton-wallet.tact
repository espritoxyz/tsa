import "./messages";
import "./constants";
import "../utils";

contract JettonWalletFeatureRich(
    owner: Address,
    minter: Address,
    balance: Int as coins,
) {
    receive(msg: JettonTransfer) {
        forceBasechain(msg.destination);
        require(sender() == self.owner, "Incorrect sender");

        let ctx = context();
        let fwdCount = 1 + sign(msg.forwardTonAmount); // msg.forwardTonAmount is coins, so it's positive
        require(
            ctx.value >
            msg.forwardTonAmount +
            fwdCount * ctx.readForwardFee() +
            getForwardFee(walletStateInitCells, walletStateInitBits, false) +
            (2 * getComputeFee(gasForTransfer, false) + minTonsForStorage),
            "Insufficient amount of TON attached",
        );

        if (msg.customPayload == null) {
            self.balance -= msg.amount;
            require(self.balance >= 0, "Incorrect balance after send");
            checkEitherForwardPayload(msg.forwardPayload);

            deploy(DeployParameters {
                value: 0,
                mode: SendRemainingValue,
                bounce: true,
                body: JettonTransferInternalWithStateInit {
                    queryId: msg.queryId,
                    amount: msg.amount,
                    sender: self.owner,
                    responseDestination: msg.responseDestination,
                    forwardTonAmount: msg.forwardTonAmount,
                    forwardPayload: msg.forwardPayload,
                }.toCell(),
                init: initOf JettonWalletFeatureRich(msg.destination, self.minter, 0),
            });

            return;
        }

        let payload = CustomPayloadWithSendModes.fromCell(msg.customPayload!!);
        let extendedActionMode = payload.mode;

        let sendAmount = msg.amount;

        if ((extendedActionMode & SendAllJettonsMode) != 0) {
            sendAmount = self.balance;
        }

        let forwardStateInit: StateInit? = null;

        if ((extendedActionMode & SendStateInitWithJettonNotification) != 0) {
            // internal transfer body creation could fail because of
            // ref overflow (forwardPayload as inline), but that's okay since
            // it will just reject
            require(payload.forwardStateInit != null, "Forward state init is null with deploy send mode");

            forwardStateInit = payload.forwardStateInit!!;
        }

        self.balance -= sendAmount;

        if ((extendedActionMode & SendNotDeployReceiversJettonWallet) != 0) {
            // this will bounce if the wallet wasn't already deployed, but it's okay and we handle it

            message(MessageParameters {
                value: 0,
                mode: SendRemainingValue,
                bounce: true,
                body: JettonTransferInternalWithStateInit {
                    queryId: msg.queryId,
                    amount: sendAmount,
                    sender: self.owner,
                    responseDestination: msg.responseDestination,
                    forwardTonAmount: msg.forwardTonAmount,
                    forwardStateInit,
                    forwardPayload: msg.forwardPayload,
                }.toCell(),
                to: contractAddress(initOf JettonWalletFeatureRich(msg.destination, self.minter, 0)),
            });

            return;
        }

        deploy(DeployParameters {
            value: 0,
            mode: SendRemainingValue,
            bounce: true,
            body: JettonTransferInternalWithStateInit {
                queryId: msg.queryId,
                amount: sendAmount,
                sender: self.owner,
                responseDestination: msg.responseDestination,
                forwardTonAmount: msg.forwardTonAmount,
                forwardStateInit,
                forwardPayload: msg.forwardPayload,
            }.toCell(),
            init: initOf JettonWalletFeatureRich(msg.destination, self.minter, 0),
        });
    }

    receive(msg: JettonTransferInternalWithStateInit) {
        self.balance += msg.amount;

        // This message should come only from master, or from other JettonWallet
        let wallet: StateInit = initOf JettonWalletFeatureRich(msg.sender, self.minter, 0);
        if (!wallet.hasSameBasechainAddress(sender())) {
            require(self.minter == sender(), "Incorrect sender");
        }

        let ctx: Context = context();
        let msgValue: Int = ctx.value;
        let tonBalanceBeforeMsg = myBalance() - msgValue;

        if (msg.forwardTonAmount > 0) {
            let fwdFee: Int = ctx.readForwardFee();
            msgValue -= msg.forwardTonAmount + fwdFee;

            if (msg.forwardStateInit != null) {
                let deployAddress = contractAddress(msg.forwardStateInit!!);

                require(deployAddress == self.owner, "Deploy address doesn't match owner address");

                deploy(DeployParameters {
                    value: msg.forwardTonAmount,
                    mode: SendPayGasSeparately,
                    bounce: false,
                    init: msg.forwardStateInit!!,
                    body: JettonNotification { // 0x7362d09c -- Remind the new Owner
                        queryId: msg.queryId,
                        amount: msg.amount,
                        sender: msg.sender,
                        forwardPayload: msg.forwardPayload,
                    }.toCell(),
                });
            } else {
                message(MessageParameters {
                    to: self.owner,
                    value: msg.forwardTonAmount,
                    mode: SendPayGasSeparately,
                    bounce: false,
                    body: JettonNotification { // 0x7362d09c -- Remind the new Owner
                        queryId: msg.queryId,
                        amount: msg.amount,
                        sender: msg.sender,
                        forwardPayload: msg.forwardPayload,
                    }.toCell(),
                });
            }
        }

        nativeReserve(max(tonBalanceBeforeMsg, minTonsForStorage), ReserveAtMost);
        // 0xd53276db -- Cashback to the original Sender
        if (msg.responseDestination != null && msgValue > 0) {
            message(MessageParameters {
                to: msg.responseDestination!!,
                value: msgValue,
                mode: SendRemainingBalance | SendIgnoreErrors,
                bounce: false,
                body: JettonExcesses { queryId: msg.queryId }.toCell(),
            });
        }
    }

    receive(msg: ProvideWalletBalance) {
        let info: VerifyInfo? = null;
        if (msg.includeVerifyInfo) {
            info = VerifyInfo {
                minter: self.minter,
                owner: self.owner,
                code: myCode(),
            };
        }

        message(MessageParameters {
            body: TakeWalletBalance {
                balance: self.balance,
                verifyInfo: info,
            }.toCell(),
            to: msg.receiver,
            value: 0,
            mode: SendRemainingValue,
        });
    }

    receive(msg: JettonBurn) {
        // we can skip forceBasechain here because with other checks in place it's not possible
        // to acquire jettons outside of basechain, so amount check is enough
        require(sender() == self.owner, "Incorrect sender");

        self.balance -= msg.amount;
        require(self.balance >= 0, "Incorrect balance after send");

        let ctx = context();
        let fwdFee: Int = ctx.readForwardFee();
        require(ctx.value > (fwdFee + 2 * getComputeFee(gasForBurn, false)), "Insufficient amount of TON attached");

        message(MessageParameters {
            to: self.minter,
            value: 0,
            mode: SendRemainingValue,
            bounce: true,
            body: JettonBurnNotification {
                queryId: msg.queryId,
                amount: msg.amount,
                sender: self.owner,
                responseDestination: msg.responseDestination,
            }.toCell(),
        });
    }

    receive(msg: ClaimTON) {
        require(sender() == self.owner, "Incorrect sender");
        nativeReserve(minTonsForStorage, ReserveExact | ReserveBounceIfActionFail);

        // we allow bounce here and don't handle it, if claim fails we just accept the TONs back
        message(MessageParameters {
            to: msg.receiver,
            value: 0,
            mode: SendRemainingBalance,
        });
    }

    bounced(msg: bounced<JettonTransferInternalWithStateInit>) {
        self.balance += msg.amount;
    }

    bounced(msg: bounced<JettonBurnNotification>) {
        self.balance += msg.amount;
    }

    get fun get_wallet_data(): JettonWalletData {
        return JettonWalletData {
            balance: self.balance,
            owner: self.owner,
            minter: self.minter,
            code: myCode(),
        };
    }
}
