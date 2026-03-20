struct SwapParameters {
    desiredAmount: Int as coins;
    payloadOnSuccess: Cell? = null;
}

message(0x2) SwapRequest {
    pool: Address;
    receiver: Address?;
    params: SwapParameters;
}

message(0x698cba08) SwapRequestTon {
    amount: Int as coins;
    action: SwapRequest;
}

message(0xac2f5a38) SwapIn {
    amount: Int as coins;
    receiver: Address;
    params: SwapParameters;
}

message(0x74f7a60) PayoutFromPool {
    otherVault: Address;
    amount: Int as coins;
    receiver: Address;
    payloadToForward: Cell? = null;
}
