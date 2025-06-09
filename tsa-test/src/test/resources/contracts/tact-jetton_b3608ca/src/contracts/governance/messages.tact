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

//=======================================================================
// TEP - 74
// https://github.com/ton-blockchain/TEPs/blob/master/text/0074-jettons-standard.md

message(0xf8a7ea5) JettonTransfer {
    queryId: Int as uint64;
    amount: Int as coins;
    destination: Address;
    responseDestination: Address?;
    customPayload: Cell?;
    forwardTonAmount: Int as coins;
    forwardPayload: Slice as remaining;
}

message(0x595f07bc) JettonBurn {
    queryId: Int as uint64;
    amount: Int as coins;
    responseDestination: Address?;
    customPayload: Cell?;
}

message(0x7362d09c) JettonNotification {
    queryId: Int as uint64;
    amount: Int as coins;
    sender: Address;
    forwardPayload: Slice as remaining;
}

message(0xd53276db) JettonExcesses {
    queryId: Int as uint64;
}

message(0x178d4519) JettonTransferInternal {
    queryId: Int as uint64;
    amount: Int as coins;
    sender: Address;
    responseDestination: Address?;
    forwardTonAmount: Int as coins;
    forwardPayload: Slice as remaining;
}

message(0x7bdd97de) JettonBurnNotification {
    queryId: Int as uint64;
    amount: Int as coins;
    sender: Address;
    responseDestination: Address?;
}

// ============== TEP-89: Jetton Wallet Discovery ==============
// https://github.com/ton-blockchain/TEPs/blob/master/text/0089-jetton-wallet-discovery.md

message(0x2c76b973) ProvideWalletAddress {
    queryId: Int as uint64;
    ownerAddress: Address;
    includeAddress: Bool;
}

message(0xd1735400) TakeWalletAddress {
    queryId: Int as uint64;
    walletAddress: Address;
    ownerAddress: Cell?; // It is Maybe ^Address, just encoded it like this
}

//=======================================================================
// Stable

message(0xd372158c) TopUp {
    queryId: Int as uint64;
}

message(0xeed236d3) SetStatus {
    queryId: Int as uint64;
    status: Int as uint4;
}

message(0x642b7d07) Mint {
    queryId: Int as uint64;
    toAddress: Address;
    tonAmount: Int as coins;
    masterMsg: JettonTransferInternal;
}

message(0x6501f354) ChangeAdmin {
    queryId: Int as uint64;
    newAdminAddress: Address;
}

message(0xfb88e119) ClaimAdmin {
    queryId: Int as uint64;
}

message(0x235caf52) CallTo {
    queryId: Int as uint64;
    toAddress: Address;
    tonAmount: Int as coins;
    masterMsg: Cell;
}

message(0x2508d66a) Upgrade {
    queryId: Int as uint64;
    newData: Cell;
    newCode: Cell;
}

message(0xcb862902) ChangeMetadataUri {
    queryId: Int as uint64;
    metadata: Slice as remaining;
}

// ============== Additional messages ==============

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
