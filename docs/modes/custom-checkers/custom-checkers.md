---
layout: default
title: Custom checkers
parent: Checking mode
nav_order: 1
---

# Writing custom checkers

The checker mode of `TSA` allows users to implement their own checkers to verify specific contract specifications.

## Checker structure

Any custom checker consists of a checker file, list of analyzed smart contracts and some options.
It could be then run with a Docker or JAR with `custom-checker` argument provided. 

### Checker file

The checker itself is a FunC file with implemented `recv_internal` method as an entrypoint for the analysis.
For verifying safety properties of analyzed contracts, 
some specific functions are provided in the [tsa_functions.fc](https://github.com/espritoxyz/tsa/blob/74502fe3ba28c0b405dc8fe0904d466fe353a61c/tsa-safety-properties-examples/src/test/resources/imports/tsa_functions.fc) file:

- `tsa_call_[x]_[y](args..., contract, method)` - an instruction for the symbolic interpreter to call the `method` of the specific `contract` 
    that returns `x` values with `y` number of the provided arguments.
- `tsa_forbid_failures` - an instruction for the symbolic interpreter to disable error detection – 
    mostly used to make initial assumptions for input/persistent data.
- `tsa_allow_failures` - an instruction for the symbolic interpreter to enable error detection -
    mostly used to check absence of the errors in the called contract or to find violation of safety properties.
- `tsa_assert(int condition)` - an instruction for the symbolic interpreter to assume the condition (making a path constraint of this condition) 
- `tsa_assert_not(int condition)` - the similar to `tsa_assert`, but negates the provided condition.
- `tsa_fetch(A value, int value_id)` - an instruction for the symbolic interpreter to fetch the specific variable by a provided index 
    to be able to retrieve its concrete value in resolved executions (rendered in the `fetched_values` part of the SARIF report).
- `tsa_mk_int(int bits, int signed)` - an instruction for the symbolic interpreter to create a new symbolic integer value 
    (accepting bits and a flag indicating is it signed) with no specific bounds.

Usually, the checker file contains a set of assumptions for the input values that would be passed
to a method of the first analyzed contract, call invocation of this method and then a set of assertions
for the return values and/or the state of the contract after the method execution.

### List of analyzed smart contracts

Analyzed smart contracts could be passed in different supported formats (Tact, FunC, Fift, BoC) and their order is important –
the first contract in the list receives the `contract_id` equals to `1`, the next – `2`, and so on.
These ids are then used in the `tsa_call` functions to specify the contract to call the method of, and in a
inter-contract communication scheme provided.

### Options

#### Inter-contract communication scheme
Inter-contract communication scheme – is required when multiple contracts are provided for the analysis.
It is a JSON file that describes what contract may send a message to what contract by what operation code.
An example of the scheme could be found in the [test module](https://github.com/espritoxyz/tsa/blob/b76343a20ce5c81e78d3e65873936ee26c148771/tsa-test/src/test/resources/intercontract/sample-intercontract-scheme.json).

#### TL-B scheme
A file with a TL-B scheme for the `recv_internal` method of the first analyzed contract could be optionally provided.
**NOTE**: TL-B scheme is supported only when using Docker, not JAR.

## Examples

Some examples of the checkers can be found with a detailed description:
- [Checking the get-method](get-method-checker)
- [Checking a state of a single contract after accepting a message](single-contract-state-checker)
- [Checking a state of multiple contracts after inter-contract communication](inter-contract-communication-checker)
