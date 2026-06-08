message(0x1001) Request {
    value: Int as uint32;
}

message(0x1002) Response {
    result: Int as uint32;
}

contract ResponseFromData(
    destination: Address,
) {
    receive(msg: Request) {
        send(SendParameters{
            to: self.destination,
            value: 0,
            mode: SendRemainingValue,
            body: Response{result: msg.value * 2}.toCell(),
        });
    }
}
