message(0x2001) RequestWithDest {
    value: Int as uint32;
    destination: Address;
}

message(0x2002) Response {
    result: Int as uint32;
}

contract ResponseFromMessage() {
    receive(msg: RequestWithDest) {
        send(SendParameters{
            to: msg.destination,
            value: 0,
            mode: SendRemainingValue,
            body: Response{result: msg.value * 2}.toCell(),
        });
    }
}
