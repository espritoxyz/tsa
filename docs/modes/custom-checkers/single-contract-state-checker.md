---
layout: default
title: Checking a state of a single contract
parent: Custom checkers
nav_order: 2
---

# Checking a state of a single contract

This guide will walk you through implementing and running a custom checker to verify that only the `reduce_balance` operation can decrement the `balance` value in a contract.

---

## Step 1: Write the Contract

The contract stores a single integer value `balance` and allows decrementing it only through the `reduce_balance` operation. Copy the following code into your editor and save it as `storage.fc`:

```c
int load_balance() inline method_id(-42) {
    var ds = get_data().begin_parse();

    return ds~load_uint(32);
}

() update_balance(int new_balance) impure inline method_id(-422) {
    var new_data = begin_cell().store_uint(new_balance, 32).end_cell();

    set_data(new_data);
}

() recv_internal(int my_balance, int msg_value, cell in_msg_full, slice in_msg_body) impure {
    if (in_msg_body.slice_empty?()) {
        ;; ignore empty messages
        return ();
    }

    int op = in_msg_body~load_uint(32);

    if (op != op::reduce_balance) {
        ;; ignore messages with unknown operation
        return ();
    }

    ;; reduce the balance by 1 in case of reduce_balance operation
    int balance = load_balance();
    balance -= 1;
    update_balance(balance);
}
```

This contract ensures that only the `reduce_balance` operation can decrement the `balance` value.

---

## Step 2: Write the Checker

To verify the behavior of the contract, we will use the following checker. Copy this code into your editor and save it as `balance_reduction_checker.fc`:

```c
() recv_internal(int my_balance, int msg_value, cell in_msg_full, slice msg_body) impure {
    tsa_forbid_failures();

    ;; ensure that we perform not a reduce_balance operation
    slice body_copy = msg_body;
    int op = body_copy~load_uint(32);
    tsa_assert_not(op == op::reduce_balance);

    int initial_balance = tsa_call_1_0(1, -42);

    ;; send a message with not reduce_balance operation
    tsa_call_0_4(my_balance, msg_value, in_msg_full, msg_body, 1, 0);

    int new_balance = tsa_call_1_0(1, -42);

    tsa_allow_failures();
    ;; check that the balance can not be reduced using not a reduce_balance operation
    throw_if(10, initial_balance != new_balance);
}
```

This checker performs the following steps:
1. Disables error detection using `tsa_forbid_failures` to make assumptions about input.
2. Ensures that the operation is not `reduce_balance` by loading the op-code and asserting with `tsa_assert_not`.
3. Retrieves the initial balance value using `tsa_call_1_0` with the method ID of `load_balance`.
4. Calls the `recv_internal` method of the contract with a non-`reduce_balance` operation.
5. Retrieves the new balance value using `tsa_call_1_0` with the method ID of `load_balance`.
6. Enables error detection using `tsa_allow_failures` to validate the result.
7. Throws an exception if the balance was changed by a non-`reduce_balance` operation.

---

## Step 3: Run the Checker

To execute the checker, open your terminal and run the following command:

{% highlight bash %}
java -jar tsa-cli.jar custom-checker \
--checker tsa-safety-properties-examples/src/test/resources/examples/step2/balance_reduction_checker.fc \
--contract func tsa-safety-properties-examples/src/test/resources/examples/step2/storage.fc \
--func-std tsa-safety-properties-examples/src/test/resources/imports/stdlib.fc \
--fift-std tsa-safety-properties-examples/src/test/resources/fiftstdlib
{% endhighlight %}

This command will:
- Run the checker on the `storage.fc` contract.
- Output the result in the SARIF format.

---

## Step 4: Analyze the Result

The result of the checker execution is a SARIF report. Here is an example of the output:

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
                        "text": "TvmFailure(exit=TVM user defined error with exit code 10, type=UnknownError, phase=COMPUTE_PHASE)"
                    },
                    "properties": {
                        "gasUsage": 2303,
                        "usedParameters": {
                            "type": "recvInternalInput",
                            "msgBody": {
                                "cell": {
                                    "data": "...",
                                    "knownTypes": [
                                        {
                                            "type": {
                                                "type": "org.usvm.test.resolver.TvmTestCellDataIntegerRead",
                                                "bitSize": 32,
                                                "isSigned": false,
                                                "endian": "BigEndian"
                                            },
                                            "offset": 0
                                        }
                                    ]
                                }
                            }
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

Key points to note:
1. The error message: `TvmFailure(exit=TVM user defined error with exit code 10, type=UnknownError, phase=COMPUTE_PHASE)` indicates a logical error in the contract.
2. The `msgBody` section contains the message body that was sent to the contract from the checker.

This report confirms that the checker detected a logical error – the `balance` was changed by a non-`reduce_balance` operation.

---
