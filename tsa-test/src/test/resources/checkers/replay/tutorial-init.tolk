import "@stdlib/gas-payments"

struct Storage {
    publicKey: uint256,
}

fun Storage.load() { return Storage.fromCell(contract.getData()); }

fun main() { }

fun onExternalMessage(inMsg: slice) {
    var signature = inMsg.loadBits(512);
    var publicKey = Storage.load().publicKey;
    assert(isSignatureValid(inMsg.hash(), signature, publicKey)) throw 35;
    acceptExternalMessage();
    val reply = createMessage({
        bounce: BounceMode.NoBounce,
        value: ton("0.05"),
        body: beginCell().endCell(),
        dest: address("EQD9ONCYURxDAV4CzRhc_Kw77_-omip_INZUQGOKlHW523p1"), // some address
    });
    reply.send(SEND_MODE_REGULAR);
}
