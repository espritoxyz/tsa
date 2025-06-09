asm fun checkEitherForwardPayload(forwardPayload: Slice) {
    LDDICT // Throws 9 if Either bit = 1 and ref is not present
    SWAP
    ISNULL
    <{
        DROP
    }> PUSHCONT
    <{
        ENDS // Also throws 9 if slice is not empty
    }> PUSHCONT
    IFELSE
}

asm fun emptyAddress(): Address { b{00} PUSHSLICE }

inline fun makeTakeWalletAddressMsg(targetJettonWallet: BasechainAddress, msg: ProvideWalletAddress): Cell {
    return beginCell()
        .storeUint(TakeWalletAddress.opcode(), 32)
        .storeUint(msg.queryId, 64)
        .storeBasechainAddress(targetJettonWallet)
        .storeMaybeRef(msg.includeAddress ? beginCell().storeAddress(msg.ownerAddress).endCell() : null)
        .endCell();
}

struct SliceBitsAndRefs {
    bits: Int;
    refs: Int;
}

asm fun calculateSliceBitsAndRefs(slice: Slice): SliceBitsAndRefs {
    SBITREFS
}
