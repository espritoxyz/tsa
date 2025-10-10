---
layout: default
title: Checking states of multiple contracts after inter-contract communication
parent: Checking mode
nav_order: 3
---

# Checking states of multiple contracts after inter-contract communication

This guide will walk you through implementing and running a custom checker to verify that a `transfer` operation decreases the sender's balance and increases the receiver's balance by the same value.

---

## Step 1: Write the Contract

The contract emulates a simple wallet with two operations:
- `transfer` – decreases the sender's balance and sends a message to the receiver.
- `receive` – increases the receiver's balance upon receiving a message.

Copy the following code into your editor and save it as `wallet.fc`:

```c
int load_balance() inline method_id(-42) {
    var ds = get_data().begin_parse();

    return ds~load_uint(32);
}

() update_balance(int new_balance) impure inline method_id(-422) {
    var new_data = begin_cell().store_uint(new_balance, 32).end_cell();

    set_data(new_data);
}

{-
  addr_std$10 anycast:(Maybe Anycast)
   workchain_id:int8 address:bits256  = MsgAddressInt;
-}
slice calc_address(cell state_init) inline {
    return begin_cell().store_uint(4, 3) ;; 0x100 : $10 + anycast (Maybe = 0)
        .store_int(0, 8)
        .store_uint(
            cell_hash(state_init), 256)
        .end_cell()
        .begin_parse();
}

builder store_msgbody_prefix_ref(builder b, cell ref) inline {
    return b.store_uint(1, 1 + 4 + 4 + 64 + 32 + 1 + 1).store_ref(ref);
}

() recv_internal(cell in_msg_full, slice in_msg_body) impure {
    if (in_msg_body.slice_empty?()) {
        ;; ignore empty messages
        return ();
    }

    slice cs = in_msg_full.begin_parse();
    int flags = cs~load_uint(4);
    if (flags & 1) {
        ;; ignore bounced messages
        return ();
    }

    int op = in_msg_body~load_uint(32);
    int value = in_msg_body~load_uint(32);

    if (op == op::transfer) {
        slice target = in_msg_body~load_msg_addr();

        ;; create a message that will decrease the balance of the target account
        var msg_body = begin_cell()
            .store_uint(op::receive, 32)
            .store_uint(value, 32)
            .store_slice(my_address())
            .end_cell();
        var msg = begin_cell()
            .store_uint(msg_flag::bounceable, 6)
            .store_slice(target)
            .store_grams(0)
            .store_msgbody_prefix_ref(msg_body);

        ;; decrease own balance
        int balance = load_balance();
        balance -= value;
        update_balance(balance);

        return send_raw_message(msg.end_cell(), 64);
    }

    if (op == op::receive) {
        slice sender = in_msg_body~load_msg_addr();

        if (equal_slice_bits(sender, my_address())) {
            ;; ignore receiving money from self
            return ();
        }

        ;; receive message increases the balance by the transferred value
        int balance = load_balance();
        balance += value;
        update_balance(balance);

        return ();
    }

    ;; unknown operation
    throw(error::unknown_action);
}
```

This contract ensures that the `transfer` operation decreases the sender's balance and the `receive` operation increases the receiver's balance.

---

## Step 2: Write the Checker

To verify the behavior of the contract, we will use the following checker. Copy this code into your editor and save it as `balance_transfer_checker.fc`:

```c

global int value;

() on_internal_message_send(int balance, int msg_value, cell in_msg_full, slice msg_body, int input_id) impure method_id {
    ;; ensure the initial message is not bounced
    slice cs = in_msg_full.begin_parse();
    int flags = cs~load_uint(4);
    tsa_assert_not(flags & 1);

    ;; ensure that we perform a [transfer] operation
    slice body_copy = msg_body;
    int op = body_copy~load_uint(32);
    tsa_assert(op == op::transfer);

    ;; ensure the transferred value has reasonable limits
    value = body_copy~load_uint(32);
    ;; save this symbolic value by the index -1 to retrieve its concrete value in the result
    tsa_fetch_value(value, -1);
    ;; do not transfer zero money
    tsa_assert(value >= 100);
    tsa_assert(value <= 1000000000);

    ;; ensure that the message body contains a target address
    slice target = body_copy~load_msg_addr();
    tsa_fetch_value(target, 0);
}

() recv_internal() impure {
    tsa_forbid_failures();

    ;; get the initial balances of the two accounts – call the `load_balance` methods with id -42 for both contracts 1 and 2 (id 0 is used for the checker)
    int first_initial_balance = tsa_call_1_0(1, -42);
    tsa_fetch_value(first_initial_balance, 1);
    int second_initial_balance = tsa_call_1_0(2, -42);
    tsa_fetch_value(second_initial_balance, 2);

    ;; ensure we have correct balances and cannot get overflows
    tsa_assert(first_initial_balance >= 0);
    tsa_assert(first_initial_balance <= 1000000000);
    tsa_assert(second_initial_balance >= 0);
    tsa_assert(second_initial_balance <= 1000000000);

    ;; send a message with a [transfer] operation
    tsa_send_internal_message(1, 0);

    ;; get the new balances of the two accounts
    int first_new_balance = tsa_call_1_0(1, -42);
    tsa_fetch_value(first_new_balance, 11);
    int second_new_balance = tsa_call_1_0(2, -42);
    tsa_fetch_value(second_new_balance, 22);

    tsa_allow_failures();

    ;; check that the balance of the first account has decreased by value
    throw_if(256, first_initial_balance - value != first_new_balance);

    ;; check that the balance of the second account has increased by value
    throw_if(257, second_initial_balance + value != second_new_balance);
}
```

This checker contains the following steps:
1. Disable error detection using `tsa_forbid_failures` to make some assumptions about input.
2. Ensure the initial message is not bounced.
3. Ensure that the operation is `transfer` by loading the op-code and making an assumption with `tsa_assert`.
4. Load the value of the transfer and make an assumption that it has reasonable limits.
5. Ensure we have a target address in the message body.
6. Get the initial balances of the two accounts using `tsa_call_1_0` and fetch them.
7. Ensure we have correct balances and cannot get overflows.
8. Pass the message to the first wallet.
9. Get the new balances of the two accounts using `tsa_call_1_0` and fetch them.
10. Enable error detection using `tsa_allow_failures` to check the result.
11. Check that the balance of the first account has decreased by 100.
12. Check that the balance of the second account has increased by 100.

---

## Step 3: Define the Inter-Contract Communication Scheme

As we have two contracts that interact with each other, we need to provide an inter-contract communication scheme.
This scheme is a JSON file that describes what contract may send a message to what contract by what operation code.
Here we have only 2 operations – `transfer` and `receive` – with opcodes `0x10000000` and `0x20000000`, respectively, 
so the scheme is very simple (sources are available [wallet-intercontract-scheme.json](https://github.com/espritoxyz/tsa/blob/74502fe3ba28c0b405dc8fe0904d466fe353a61c/tsa-safety-properties-examples/src/test/resources/examples/step3/wallet-intercontract-scheme.json)):

```json
[
    {
        "id": 1,
        "handlers": [
            {
                "op": "10000000",
                "destinations": [2]
            },
            {
                "op": "20000000",
                "destinations": [2]
            }
        ]
    }
]
```

Here we describe only the first wallet as we do not expect that the second wallet will send any messages.

---

## Step 4: Run the Checker

To execute the checker, open your terminal and run the following command:

{% highlight bash %}
java -jar tsa-cli.jar custom-checker \
--checker tsa-safety-properties-examples/src/test/resources/examples/step3/balance_transfer_checker.fc \
--contract func tsa-safety-properties-examples/src/test/resources/examples/step3/wallet.fc \
--contract func tsa-safety-properties-examples/src/test/resources/examples/step3/wallet.fc \
--scheme tsa-safety-properties-examples/src/test/resources/examples/step3/wallet-intercontract-scheme.json \
--func-std tsa-safety-properties-examples/src/test/resources/imports/stdlib.fc \
--fift-std tsa-safety-properties-examples/src/test/resources/fiftstdlib
{% endhighlight %}


This command will:
- Run the checker on two instances of the `wallet.fc` contract.
- Use the inter-contract communication scheme to simulate interactions.
- Output the result in the SARIF format.

Note that we pass the inter-contract communication scheme of the two wallets and
provide the same contract twice to analyze the interaction between different accounts.

---

## Step 5: Analyze the Result

The result of the checker execution is a SARIF report that contains the following information:

```json
{
    "$schema": "https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json",
    "version": "2.1.0",
    "runs": [
        {
            "results": [
                {
                    "level": "error",
                    "message": {
                        "text": "TvmFailure(exit=TVM user defined error with exit code 257, type=UnknownError, phase=COMPUTE_PHASE)"
                    },
                    "properties": {
                        "gasUsage": 7520,
                        "usedParameters": {
                            "type": "recvInternalInput",
                            "srcAddress": {
                                "cell": {
                                    "data": "100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
                                }
                            },
                            "msgBody": {
                                "cell": {
                                    "data": "00010000000000000000000000000000000000000000000000000000011001001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                                    "knownTypes": [
                                        {
                                            "type": {
                                                "type": "org.usvm.test.resolver.TvmTestCellDataIntegerRead",
                                                "bitSize": 32,
                                                "isSigned": false,
                                                "endian": "BigEndian"
                                            },
                                            "offset": 0
                                        },
                                        {
                                            "type": {
                                                "type": "org.usvm.test.resolver.TvmTestCellDataIntegerRead",
                                                "bitSize": 32,
                                                "isSigned": false,
                                                "endian": "BigEndian"
                                            },
                                            "offset": 32
                                        },
                                        {
                                            "type": {
                                                "type": "org.usvm.test.resolver.TvmTestCellDataMsgAddrRead"
                                            },
                                            "offset": 64
                                        }
                                    ]
                                }
                            },
                            "msgValue": "73786976294838206464",
                            "bounce": false,
                            "bounced": false,
                            "ihrDisabled": false,
                            "ihrFee": "0",
                            "fwdFee": "0",
                            "createdLt": "0",
                            "createdAt": "0"
                        },
                        "fetchedValues": {
                            "-1": "100",
                            "0": {
                                "type": "org.usvm.test.resolver.TvmTestSliceValue",
                                "cell": {
                                    "data": "0001000000000000000000000000000000000000000000000000000001100100100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
                                },
                                "dataPos": 64
                            },
                            "1": "536870912",
                            "2": "0",
                            "11": "536870812",
                            "22": "0"
                        },
                        "resultStack": [
                            "0"
                        ]
                    },
                    "ruleId": "user-defined-error"
                }
            ],
            "tool": {
                "driver": {
                    "name": "TSA",
                    "organization": "Explyt"
                }
            }
        }
    ]
}
```

We are interested in lines with the following indices:
- `10` – `TvmFailure(exit=TVM user defined error with exit code 257, type=UnknownError, phase=COMPUTE_PHASE)`.
- `16` – `srcAddress` – the address of the sender with a value `100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000`
- `63` – `fetchedValues` with index `0` that corresponds to the concrete value of the `target`.

The found error means that the balance of the second account has not increased by the value after the transfer operation.
It happens when an address of the sender equals to the address of the receiver – it can be checked that `srcAddress` and `target` are the same.

After analyzing the source code of out contract, we could discover a logical error in the wallet code:
{% highlight c %}
if (equal_slice_bits(sender, my_address())) {
    ;; ignore receiving money from self
    return ();
}
{% endhighlight %}

This block of code leads to decreasing the balance of the sender but does not increase the balance of the receiver.
