---
layout: default
title: Checking mode
parent: Use cases
nav_order: 3
---

# Checking mode

A more advanced mode of `TSA` operation is the **checking mode** that allows users implementing their own checkers 
to validate specific contract specifications.
This mode has its own specific format of the input files and a set of functions that are used to implement the checkers.

## Checker structure

Any custom checker consists of a checker file, list of analyzed smart contracts and some options.
It could be then run with a Docker or JAR with `custom-checker` argument provided. 

### Checker file

The checker itself is a FunC file with implemented `recv_internal` method as an entrypoint for the analysis.
For verifying safety properties of analyzed contracts, intrinsic functions are provided.
The more detailed overview of these functions can be found [here](../reference/checker-functions.md).

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
An example of the scheme could be found in the [test module](https://github.com/espritoxyz/tsa/blob/master/tsa-test/src/test/resources/intercontract/sample/sample-intercontract-scheme.json).
The in-depth description of the format can be found [here](../reference/inter-contract.md).

#### TL-B scheme
A file with a TL-B scheme for the `recv_internal` method of the first analyzed contract could be optionally provided.
**NOTE**: TL-B scheme is supported only when using Docker, not JAR.
