message Add {
    amount: Int as uint32;
}

contract Counter(
    // Persistent state variables declared via the
    // contract parameters syntax, which was introduced in v1.6.0
    //
    // See: https://docs.tact-lang.org/book/contracts/#parameters
    id: Int as uint32,
    counter: Int as uint32,
) {
    // Empty receiver for the deployment,
    // which expects the `null` message body
    receive() {
        // Forward the remaining value in the
        // incoming message back to the sender
        cashback(sender());
    }

    receive(msg: Add) {
        self.counter += msg.amount;

	let x: Int = 10;
	while (x > 0) {
        cashback(sender());
        x -= 1;
	}

        // Forward the remaining value in the
        // incoming message back to the sender
        cashback(sender());
    }

    get fun counter(): Int {
        return self.counter;
    }

    get fun id(): Int {
        return self.id;
    }
}
