---
layout: default
title: Checking a state of a single contract
parent: Custom checkers
nav_order: 2
---

# Checking a state of a single contract

## Contract

Let's consider a simple smart-contract that stores a single int value `balance`,
that could be decremented when processing `reduce_balance` operation, but no other operations could do it.

One could implement such a contract in the following way (sources are available [storage.fc](https://github.com/espritoxyz/tsa/blob/74502fe3ba28c0b405dc8fe0904d466fe353a61c/tsa-safety-properties-examples/src/test/resources/examples/step2/storage.fc)):

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
    }

    ;; reduce the balance by 1 in case of [reduce_balance] operation
    int balance = load_balance();
    balance -= 1;
    update_balance(balance);
}
```

## Checker

Now we want to ensure that only the `reduce_balance` operation could decrement the `balance` value –
in other words, any other operation should not change the `balance` value.

To implement such a checker, one could use the following FunC code (sources are available [balance_reduction_checker.fc](https://github.com/espritoxyz/tsa/blob/74502fe3ba28c0b405dc8fe0904d466fe353a61c/tsa-safety-properties-examples/src/test/resources/examples/step2/balance_reduction_checker.fc)):

```c
() recv_internal(int my_balance, int msg_value, cell in_msg_full, slice msg_body) impure {
    tsa_forbid_failures();

    ;; ensure that we perform not a [reduce_balance] operation
    slice body_copy = msg_body;
    int op = body_copy~load_uint(32);
    tsa_assert_not(op == op::reduce_balance);

    int initial_balance = tsa_call_1_0(1, -42);

    ;; send a message with not [reduce_balance] operation
    tsa_call_0_4(my_balance, msg_value, in_msg_full, msg_body, 1, 0);

    int new_balance = tsa_call_1_0(1, -42);

    tsa_allow_failures();
    ;; check that the balance can not be reduced using not a [reduce_balance] operation
    throw_if(-42, initial_balance != new_balance);
}
```

This checker contains the following steps:
1. Disable error detection using `tsa_forbid_failures` to make some assumptions about input.
2. Ensure that the operation is not `reduce_balance` by loading the op-code and making an assumption with `tsa_assert_not`.
3. Get the initial balance value using `tsa_call_1_0` with a method id of the `load_balance` method.
4. Call the `recv_internal` method of the analyzed contract with required op-code.
5. Get the new balance value using `tsa_call_1_0` with a method id of the `load_balance` method.
6. Enable error detection using `tsa_allow_failures` to check the result.
7. Throw an exception if the balance was changed with not a `reduce_balance` operation.

## Running the checker

To run this checker, you need to have either the `tsa-cli.jar` JAR file downloaded/built or the Docker container pulled.
To make it more clear, let's use the JAR here – run this command from the root of the repository:

```bash
java -jar tsa-cli.jar custom-checker \
--checker tsa-safety-properties-examples/src/test/resources/examples/step2/balance_reduction_checker.fc \
--contract func tsa-safety-properties-examples/src/test/resources/examples/step2/storage.fc \
--func-std tsa-safety-properties-examples/src/test/resources/imports/stdlib.fc \
--fift-std tsa-safety-properties-examples/src/test/resources/fiftstdlib
```

This command will run the checker on the `storage.fc` contract and output the result in the SARIF format.

## Result

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
                        "text": "TvmFailure(exit=TVM user defined error with exit code -42, type=UnknownError, phase=COMPUTE_PHASE)"
                    },
                    "properties": {
                        "gasUsage": 2303,
                        "usedParameters": {
                            "type": "recvInternalInput",
                            "srcAddress": {
                                "cell": {
                                    "data": "100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
                                }
                            },
                            "msgBody": {
                                "cell": {
                                    "data": "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
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

We are interested in the following lines:
- `TvmFailure(exit=TVM user defined error with exit code -42, type=UnknownError, phase=COMPUTE_PHASE)`.
- `msgBody` contains the message body that was sent to the contract from the checker.

This report means that the checker has detected a logical error in the contract – 
the balance was changed with not a `reduce_balance` operation that is stored in the `msgBody` input value.

After analyzing the source code of out contract, we could discover that an early-return statement is missing in the `recv_internal` method,
when processing unknown operations, that leads to the balance change.
