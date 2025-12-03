---
layout: default
title: Checker functions
parent: Reference
nav_order: 5
---

# Calls

These intrinsic instructions are only valid in the checker contract.
They are declared in the generated file [tsa_functions.fc](https://github.com/espritoxyz/tsa/blob/master/tsa-safety-properties-examples/src/test/resources/imports/tsa_functions.fc).


## Calls Table

| call signature                                              | description                                                                                                                     |
|-------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| int tsa_mk_int(int bits, int signed)                        | create a symbolic integer value                                                                                                 |
| () tsa_forbid_failures()                                    | starts the assumption scope                                                                                                     |
| () tsa_allow_failures()                                     | ends the assumption scope                                                                                                       |
| () tsa_assert(int condition)                                | creates an assumption                                                                                                           | 
| () tsa_assert_not(int condition)                            | creates an assumption                                                                                                           | 
| forall A -> () tsa_fetch_value(A value, int value_id)       | captures the value and binds it to the index value_id; the captured value would be displayed in the output                      |
| () tsa_send_internal_message(int contract_id, int input_id) | sends an internal message to a contract with the given id; input_id might be used in on_internal_message_send handler           |
| () tsa_send_external_message(int contract_id, int input_id) | sends an external message to a contract with the given id; input_id might be used in on_external_message_send handler           |
| cell tsa_get_c4(int contract_id)                            | gets the c4 of the contract                                                                                                     |
| tsa_call_X_Y(args... , int contract_id, int method_id)      | call the method *method_id* of the specified smart contract with Y input parameters (passed in *args...*) that returns X values |

## Notes

`tsa_fetch_value` should be called exactly once per execution with the same `input_id`.

An *assumption* implies that we only consider the executions that satisfy the assumptions.
Within the assumption scope, the assumption is that no failure happened.
For example, when we perform a `load_int` instruction within the assuption scope, only the executions where the loading
occured without any exception (like cell underflow) would be considered.

# Handlers

## On internal message send

### Signature:

```
() on_internal_message_send(int balance, int msg_value, cell in_msg_full, slice msg_body, int input_id)
```

### Semantics

Is called after the message was sent by the checker to the receiver contract, but before its first instruction was
executed.
Its arguments are the same (with the addition of `input_id`) as `recv_internal` would have after receiving the message.

Should be used to verify the input parameters of the `recv_internal` call in the context of the receiver contract.

### Note

You probably do not want to verigy the global state of contracts (e.g. its balance) in this handler, as it will be
called on each of the sent messages.
It is recommended to perform such checks *before* sending the message (i.e. before the call to `send_internal_message`)

## On external message send

### Signature:

```
() on_external_message_send(int balance, int msg_value, cell in_msg_full, slice msg_body, int input_id)
```

### Semantics

Same as for `on_internal_message_send`, but for external messages.

## On out message

### Signature

```
() on_out_message(int contract_message_number, cell msg_full, slice msg_body, int receiver_contract_id, int sender_contract_id)
```

### Semantics

Is called after the action phase of the contract on each message that was eventually sent into the network, regardless
of whether the receiver is one of the contracts under test.
The `contract_message_number` is the order number of the message relative within the action phase it was sent in.
`receiver_contract_id` might be -1 if the receiving contract was resolved to be not within the contracts under test.
