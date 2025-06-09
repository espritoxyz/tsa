struct JettonData {
    totalSupply: Int;
    mintable: Bool;
    owner: Address;
    content: Cell;
    jettonWalletCode: Cell;
}

struct JettonWalletData {
    balance: Int;
    owner: Address;
    minter: Address;
    code: Cell;
}

struct MaybeAddress {
    address: Address?;
}

message(4) JettonUpdateContent {
    queryId: Int as uint64;
    content: Cell;
}

struct CustomPayloadWithSendModes {
    mode: Int as uint32;
    forwardStateInit: StateInit?;
}

message(0xf8a7ea5) JettonTransfer {
    queryId: Int as uint64;
    amount: Int as coins;
    destination: Address;
    responseDestination: Address?;
    customPayload: Cell?;
    forwardTonAmount: Int as coins;
    forwardPayload: Slice as remaining;
}

message(0x178d4519) JettonTransferInternalWithStateInit {
    queryId: Int as uint64;
    amount: Int as coins;
    sender: Address;
    responseDestination: Address?;
    forwardTonAmount: Int as coins;
    forwardStateInit: StateInit?;
    forwardPayload: Slice as remaining;
}

message(0x7362d09c) JettonNotification {
    queryId: Int as uint64;
    amount: Int as coins;
    sender: Address;
    forwardPayload: Slice as remaining;
}

message(0x595f07bc) JettonBurn {
    queryId: Int as uint64;
    amount: Int as coins;
    responseDestination: Address?;
    customPayload: Cell?;
}

message(0x7bdd97de) JettonBurnNotification {
    queryId: Int as uint64;
    amount: Int as coins;
    sender: Address;
    responseDestination: Address?;
}

message(0xd53276db) JettonExcesses {
    queryId: Int as uint64;
}

message(0x2c76b973) ProvideWalletAddress {
    queryId: Int as uint64;
    ownerAddress: Address;
    includeAddress: Bool;
}

message(0xd1735400) TakeWalletAddress {
    queryId: Int as uint64;
    walletAddress: Address;
    ownerAddress: Cell?; //It is Maybe ^Address, just encoded it like this
}

message(0x642b7d07) Mint {
    queryId: Int as uint64;
    receiver: Address;
    tonAmount: Int as coins;
    mintMessage: JettonTransferInternalWithStateInit;
}

message(22) CloseMinting {}

message(3) ChangeOwner {
    queryId: Int as uint64;
    newOwner: Address;
}

// provide_wallet_balance#7ac8d559 receiver:MsgAddress include_verify_info:Bool = InternalMsgBody
message(0x7ac8d559) ProvideWalletBalance {
    receiver: Address;
    includeVerifyInfo: Bool;
}

struct VerifyInfo {
    owner: Address;
    minter: Address;
    code: Cell;
}

// verify_info$_ owner:MsgAddress minter:MsgAddress code:^Cell = VerifyInfo
// take_wallet_balance#ca77fdc2 balance:Coins verify_info:(Maybe VerifyInfo) = InternalMsgBody
message(0xca77fdc2) TakeWalletBalance {
    balance: Int as coins;
    verifyInfo: VerifyInfo?;
}

// claim_ton#0393b1ce receiver:MsgAddress = InternalMsgBody
message(0x0393b1ce) ClaimTON {
    receiver: Address;
}

// extended messages, encoded in customPayload

const SendAllJettonsMode: Int = 0x1;
const SendNotDeployReceiversJettonWallet: Int = 0x2;
const SendStateInitWithJettonNotification: Int = 0x4;
