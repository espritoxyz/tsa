import "@stdlib/gas-payments"
import "../../../../../../../../../bin/smartcont/tolk-stdlib/common"

struct Storage {
    publicKey: uint256,
    seqno: int32,
}

struct GetMoney {
    amount: int32,
}

fun Storage.load() {
    return Storage.fromCell(contract.getData());
}

fun Storage.save(self) {
    contract.setData(self.toCell());
}

get fun getSeqno() {
    return Storage.load().seqno;
}

fun main() {
}

fun onExternalMessage(inMsg: slice) {
    var signature = inMsg.loadBits(512);
    val storage = Storage.load();
    var publicKey = storage.publicKey;
    assert (isSignatureValid(inMsg.hash(), signature, publicKey)) throw 35;

    var msgSeqno = inMsg.loadUint(32);
    var storedSeqno = storage.seqno;
    assert (msgSeqno == storedSeqno) throw 36;

    acceptExternalMessage();
    Storage {
        publicKey: publicKey, seqno: storedSeqno + 1
    }.save();
    commitContractDataAndActions();

    val reply = createMessage({
        bounce: BounceMode.NoBounce,
        value: ton("0.05"),
        body: GetMoney {
            amount: inMsg.loadUint(32)
        },
        dest: address("EQD9ONCYURxDAV4CzRhc_Kw77_-omip_INZUQGOKlHW523p1"), // some address
    });
    reply.send(SEND_MODE_REGULAR);
}
