import "@stdlib/ownable";
import "@stdlib/deploy";
import "./jetton_wallet";
import "./constants";
import "./messages";

asm fun emptyAddress(): Address { b{00} PUSHSLICE }

struct JettonMasterState {
    totalSupply: Int as coins;
    mintable: Bool;
    adminAddress: Address;
    jettonContent: Cell;
    jettonWalletCode: Cell;
}

//Actually this contract has OwnableTransferable functionality
//but this logic is implemented without OwnableTransferable trait
//to match refference implementation in terms of exit codes.
contract JettonMinter with MinterExitcodes, GasConstants {
    totalSupply: Int as coins;
    mintable: Bool;
    owner: Address;
    jettonContent: Cell;

    init(owner: Address, jettonContent: Cell) {
        self.totalSupply = 0;
        self.mintable = true;
        self.owner = owner;
        self.jettonContent = jettonContent;
    }

    receive(msg: TokenBurnNotification) {
        //Check that the message is from msg.sender's jetton_wallet
        nativeThrowUnless(self.UnauthorizedBurn, sender() == self.getJettonWalletByOwner(msg.sender));

        self.totalSupply -= msg.amount;
        send(SendParameters{
        to: msg.response_destination,
        value: 0,
        bounce: false,
        mode: SendRemainingValue | SendIgnoreErrors, //ignore errors, because supply already been updated
        body: TokenExcesses{
        query_id: msg.query_id
        }.toCell()
        });
    }
    receive(msg: CustomChangeOwner) {
        // Check if the sender is the owner
        nativeThrowUnless(self.IncorrectSender, sender() == self.owner);

        // Update owner
        self.owner = msg.newOwner;
    }

    receive(msg: TokenUpdateContent) {
        //Only owner may update content.
        nativeThrowUnless(self.IncorrectSender, sender() == self.owner);
        self.jettonContent = msg.content; // Update content
    }

    // https://github.com/ton-blockchain/TEPs/blob/master/text/0089-jetton-wallet-discovery.md
    receive(msg: ProvideWalletAddress) {
        nativeThrowUnless(self.InsufficientGasForDiscovery, context().value >= self.gasForDiscovery);
        let includedAddress: Address? = null;
        let workchain: Int = parseStdAddress(msg.owner_address.asSlice()).workchain;
        //Note, that emptyAddress != null, it is different values.
        //We do like that according to TEP above
        let targetJettonWallet: Address = emptyAddress();

        if(workchain == 0) {
            //Only in this case (address is from basechain) we can calculate the address
            targetJettonWallet = contractAddress(initOf JettonWallet(msg.owner_address, myAddress()));
        }
        if (msg.include_address) {
            includedAddress = msg.owner_address;
        }
        send(SendParameters{
        to: sender(),
        value: 0,
        mode: SendRemainingValue,
        body: self.takeWalletBody(targetJettonWallet, includedAddress, msg.query_id)
        });
    }
    receive(msg: Mint) {
        nativeThrowUnless(self.IncorrectSender, sender() == self.owner); // Allow minting only by owner

        //We don't use nativeThrowUnless here as 'mintable' flag is implementation-defined
        //And not present in token-contract https://github.com/ton-blockchain/token-contract/tree/main/ft
        require(self.mintable, "Not mintable");

        //Maybe we should check that msg.value is enough to cover the gas fees
        //But there is no such check in token-contract,
        self.totalSupply += msg.amount; // Update total supply

        let winit: StateInit = self.getJettonWalletInit(msg.receiver);

        send(SendParameters{
        to: contractAddress(winit),
        value: 0,
        bounce: true,
        mode: SendRemainingValue,
        body: TokenTransferInternal{
        query_id: msg.query_id,
        amount: msg.amount,
        from: myAddress(),
        response_destination: self.owner, // Owner is minting, so send excess to owner
        forward_ton_amount: 1, // 1 nanoton is enough to send a notification
        forward_payload: emptySlice()
        }.toCell(),
        code: winit.code,
        data: winit.data
        });
    }

    bounced(msg: bounced<TokenTransferInternal>){
        self.totalSupply -= msg.amount;
    }

    //https://github.com/ton-blockchain/TEPs/blob/master/text/0089-jetton-wallet-discovery.md#scheme
    //take_wallet_address#d1735400 query_id:uint64 wallet_address:MsgAddress owner_address:(Maybe ^MsgAddress) = InternalMsgBody;
    inline fun takeWalletBody(targetJettonWallet: Address, includedAddress: Address?, query_id: Int): Cell {
        let body: Builder = beginCell()
        .storeUint(0xd1735400, 32) // takeWalletBody opcode
        .storeUint(query_id, 64)
        .storeSlice(targetJettonWallet.asSlice());
        if (includedAddress != null) {
            let includedAddressCell: Cell = beginCell().storeSlice(includedAddress!!.asSlice()).endCell();
            body = body.storeBit(true); // Maybe bit
            body = body.storeRef(includedAddressCell);
        } else {
            body = body.storeBit(false); // Maybe bit
        }
        return body.endCell();
    }

    inline fun getJettonWalletByOwner(jetton_wallet_owner: Address): Address {
        let jwInit: StateInit = self.getJettonWalletInit(jetton_wallet_owner);
        return contractAddress(jwInit);
    }

    inline fun getJettonWalletInit(address: Address): StateInit {
        return initOf JettonWallet(address, myAddress());
    }

    get fun get_jetton_data(): JettonMasterState {
        return JettonMasterState {
        totalSupply: self.totalSupply,
        mintable: self.mintable,
        adminAddress: self.owner,
        jettonContent: self.jettonContent,
        //Owner may be any address, what matters here is the code, not data.
        jettonWalletCode: initOf JettonWallet(myAddress(), myAddress()).code
        }
    }
    get fun get_wallet_address(ownerAddress: Address): Address {
        return self.getJettonWalletByOwner(ownerAddress);
    }
    get fun owner(): Address {
        return self.owner;
    }
}