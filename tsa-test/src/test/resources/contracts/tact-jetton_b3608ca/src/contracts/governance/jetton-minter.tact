// https://github.com/ton-blockchain/TEPs/blob/master/text/0089-jetton-wallet-discovery.md

import "./jetton-wallet";
import "./messages";
import "./constants";
import "../utils";

const Workchain: Int = 0;

struct JettonMinterState {
    totalSupply: Int as coins;
    mintable: Bool;
    adminAddress: Address;
    jettonContent: Cell;
    jettonWalletCode: Cell;
}

contract GovernanceJettonMinter(
    totalSupply: Int as coins,
    adminAddress: Address,
    nextAdminAddress: Address?,
    jettonContent: Cell,
) {
    receive(msg: Mint) {
        require(sender() == self.adminAddress, "Incorrect sender");
        require(parseStdAddress(msg.toAddress.asSlice()).workchain == Workchain, "Wrong workchain");

        // No need to check the opcode here, as Tact will check it automatically
        // throw_unless(error::invalid_op, master_msg_slice~load_op() == op::internal_transfer);

        checkEitherForwardPayload(msg.masterMsg.forwardPayload);

        let fwdCount = 1 + sign(msg.masterMsg.forwardTonAmount);
        let ctx = context();
        require(
            ctx.value >
            msg.masterMsg.forwardTonAmount +
            fwdCount * ctx.readForwardFee() +
            getForwardFee(walletStateInitCells, walletStateInitBits, false) +
            (2 * getComputeFee(gasForTransfer, false) + minTonsForStorage),
            "Insufficient amount of TON attached",
        );

        self.totalSupply += msg.masterMsg.amount;

        deploy(DeployParameters {
            value: 0,
            bounce: true,
            mode: SendRemainingValue | SendBounceIfActionFail,
            body: msg.masterMsg.toCell(),
            init: getJettonWalletInit(msg.toAddress),
        });
    }

    receive(msg: JettonBurnNotification) {
        let sender = parseStdAddress(sender().asSlice());
        let wallet = getJettonBasechainWalletByOwner(msg.sender);

        require(sender.workchain == Workchain && sender.address == wallet.hash!!, "Unauthorized burn");

        self.totalSupply -= msg.amount;

        if (msg.responseDestination != null) {
            message(MessageParameters {
                to: msg.responseDestination!!,
                body: JettonExcesses { queryId: msg.queryId }.toCell(),
                value: 0,
                bounce: false,
                mode: SendRemainingValue | SendIgnoreErrors, // ignore errors, because supply has already been updated
            });
        }
    }

    receive(msg: ProvideWalletAddress) {
        let ownerWorkchain: Int = parseStdAddress(msg.ownerAddress.asSlice()).workchain;

        let targetJettonWallet: BasechainAddress = (ownerWorkchain == Workchain)
            ? contractBasechainAddress(initOf JettonWalletGovernance(0, 0, msg.ownerAddress, myAddress()))
            : emptyBasechainAddress();

        message(MessageParameters {
            bounce: false,
            body: makeTakeWalletAddressMsg(targetJettonWallet, msg),
            to: sender(),
            value: 0,
            mode: SendRemainingValue | SendBounceIfActionFail,
        });
    }

    receive(msg: ChangeAdmin) {
        require(sender() == self.adminAddress, "Incorrect sender");
        self.nextAdminAddress = msg.newAdminAddress;
    }

    receive(msg: ClaimAdmin) {
        require(sender() == self.nextAdminAddress, "Not next admin");
        self.adminAddress = sender();
        self.nextAdminAddress = null;
    }

    // can be used to lock, unlock or redeem funds
    receive(msg: CallTo) {
        require(sender() == self.adminAddress, "Incorrect sender");
        let masterMsgSlice = msg.masterMsg.beginParse();
        let op = masterMsgSlice.preloadUint(32);

        if (op == JettonTransfer.opcode()) {
            let msgToSend = JettonTransfer.fromSlice(masterMsgSlice);

            checkEitherForwardPayload(msgToSend.forwardPayload);

            let ctx = context();
            let fwdCount = 1 + sign(msgToSend.forwardTonAmount);
            require(
                ctx.value >
                msgToSend.forwardTonAmount +
                fwdCount * ctx.readForwardFee() +
                (2 * getComputeFee(gasForTransfer, false) + minTonsForStorage),
                "Insufficient amount of TON attached",
            );

            deploy(DeployParameters {
                bounce: false,
                value: msg.tonAmount,
                mode: SendPayGasSeparately | SendBounceIfActionFail,
                body: msg.masterMsg,
                init: getJettonWalletInit(msg.toAddress),
            });
        } else if (op == JettonBurn.opcode()) {
            // It is needed to validate the message
            JettonBurn.fromSlice(masterMsgSlice);

            let ctx = context();
            require(
                ctx.value > (ctx.readForwardFee() + 2 * getComputeFee(gasForBurn, false)),
                "Insufficient amount of TON attached",
            );

            deploy(DeployParameters {
                bounce: false,
                value: msg.tonAmount,
                mode: SendPayGasSeparately | SendBounceIfActionFail,
                body: msg.masterMsg,
                init: getJettonWalletInit(msg.toAddress),
            });
        } else if (op == SetStatus.opcode()) { // SetStatus opcode
            // It is needed to validate the message
            SetStatus.fromSlice(masterMsgSlice);

            deploy(DeployParameters {
                bounce: false,
                value: msg.tonAmount,
                mode: SendPayGasSeparately | SendBounceIfActionFail,
                body: msg.masterMsg,
                init: getJettonWalletInit(msg.toAddress),
            });
        } else {
            throw(0xffff); // error::invalid_op
        }
    }

    receive(msg: ChangeMetadataUri) {
        require(sender() == self.adminAddress, "Incorrect sender");
        self.jettonContent = msg.metadata.asCell();
    }

    receive(msg: Upgrade) {
        require(sender() == self.adminAddress, "Incorrect sender");
        setData(msg.newData);
        setCode(msg.newCode);
    }

    receive(_: TopUp) {}

    bounced(msg: bounced<JettonTransferInternal>) {
        self.totalSupply -= msg.amount;
    }

    get fun get_jetton_data(): JettonMinterState {
        return JettonMinterState {
            totalSupply: self.totalSupply,
            mintable: true,
            adminAddress: self.adminAddress,
            jettonContent: self.jettonContent,
            jettonWalletCode: codeOf JettonWalletGovernance,
        };
    }

    get fun get_wallet_address(ownerAddress: Address): Address {
        return getJettonWalletByOwner(ownerAddress);
    }

    get fun get_next_admin_address(): Address? {
        return self.nextAdminAddress;
    }
}

inline fun getJettonWalletInit(address: Address): StateInit {
    return initOf JettonWalletGovernance(0, 0, address, myAddress());
}

inline fun getJettonWalletByOwner(jettonWalletOwner: Address): Address {
    return contractAddress(getJettonWalletInit(jettonWalletOwner));
}

inline fun getJettonBasechainWalletByOwner(jettonWalletOwner: Address): BasechainAddress {
    return contractBasechainAddress(getJettonWalletInit(jettonWalletOwner));
}

asm fun setData(newData: Cell) {
    c4 POP
}

asm fun setCode(newCode: Cell) {
    SETCODE
}
