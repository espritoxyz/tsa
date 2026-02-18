
fun constructBouncedMessage(opcode: int): cell {
    return beginCell()
        .storeUint(0x18, 6) // 0x8 = 0b1000 = bounce flag
        .storeAddress(address("EQD9ONCYURxDAV4CzRhc_Kw77_-omip_INZUQGOKlHW523p1")) // the address of the recipient contract
        .storeCoins(100000000)
        .storeUint(0, 1 + 4 + 4 + 64 + 32 + 1 + 1) // default message headers
        .storeUint(opcode, 32)
        .endCell();
}

fun onInternalMessage(fullMsg: cell, msgBody: slice) {
    var message = constructBouncedMessage(0x100);
    sendRawMessage(message, 0);
}
