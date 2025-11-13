---
layout: default
title: Tests generation mode
parent: Use cases
nav_order: 2
---

# Test Generation

This document provides details about the CLI command for generating tests using [TSA](https://github.com/espritoxyz/tsa). 
The tool analyzes failing executions (such as TVM exceptions or user-defined errors) and automatically generates test cases for them.

## Overview

The tool generates tests that use the [Sandbox](https://github.com/ton-org/sandbox) framework to emulate the blockchain environment. These tests replicate specific failing executions by initializing the contract in the required state and sending internal messages that trigger the failure.

Additionally, the test contract wrapper is generated.

**Note:** Test generation is currently supported only for `recv_internal` and `recv_external`.

## Command Example

{% highlight bash %}
java -jar tsa-cli.jar test-gen \
  -p path/to/project \
  --contract FunC contracts/contract.func \
  --fift-std path/to/fiftstdlib
{% endhighlight %}

## Output

After executing the command, a test file will be generated in the `tests` directory of the specified project.

The test filename is derived from the contract filename. 
For example, `jetton-minter.fc` will result in `JettonMinter.spec.ts`.

## Running the Generated Tests

You can run the generated tests using one of the following commands, depending on your project's configuration:

{% highlight bash %}
yarn jest tests/TestFile.spec.fc
{% endhighlight %}

{% highlight bash %}
npm test tests/TestFile.spec.fc
{% endhighlight %}

---

## Example

For the following sample contract:

```c
#include "stdlib.func";

(slice, int) safe_load(slice sl, int size) {
    if (sl.slice_bits() < size) {
        return (sl, 0);
    }

    return sl.load_uint(size);
}

int load_data() {
    slice ds = get_data().begin_parse();
    return ds~safe_load(8);
}

() recv_internal(slice in_msg_body) impure {
    int op = in_msg_body~safe_load(32);

    if (op == 0xffffffff) {
        int stored_data = load_data();

        if (stored_data == 255) {
            throw(333);
        }
    }

    return ();
}
```

The tool generates the following tests:

```ts
// ...

describe('tsa-tests-recv-internal', () => {
    it('user-defined-error-333-0', async () => {
        blockchain.now = 1712318909
        
        const data: Cell = beginCell().storeUint(BigInt("0b11111111"), 8).endCell()
        const contractAddr: Address = Address.parse("0:0000000000000000000000000000000000000000000000000000000000000000")
        const contractBalance: bigint = 10000000n
        
        const contract: SandboxContract<JettonMinter> = blockchain.openContract(new JettonMinter(contractAddr, code, data))
        await contract.initializeContract(blockchain, contractBalance)
        
        const srcAddr: Address = Address.parse("0:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        const msgBody: Cell = beginCell().storeUint(BigInt("0b11111111111111111111111111111111"), 32).endCell()
        const msgCurrency: bigint = 10000000n
        const bounce: boolean = false
        const bounced: boolean = false
        
        const sendMessageResult: SendMessageResult = await contract.internal(blockchain, srcAddr, msgBody, msgCurrency, bounce, bounced)
        expect(sendMessageResult.transactions).toHaveTransaction({
            from: srcAddr,
            to: contractAddr,
            exitCode: 333,
        })
    })
})

// ...
```

In this test, the contract is deployed via the sandbox with a contract data containing a number `255` and 
the `recv_internal` function is called with a message body containing the op-code `0xffffffff` that triggers 
the user-defined error with a code `333` in the contract.
