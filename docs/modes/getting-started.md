---
layout: default
title: Getting started
nav_order: 2
---

# Getting started

Before starting using TSA, ensure it is [installed](../installation) properly.

The primary purpose of TSA is to enhance the reliability of the TON blockchain and its smart contracts. 
To achieve this goal, TSA can analyze trustworthy contracts in an automated mode (without user involvement) to identify errors, 
or in a semi-automated mode (with user-defined custom checkers) to check smart contracts for vulnerabilities and reliability.

It has three modes of operation:
- Runtime errors detection mode (for different sources of input: Tact, FunC, Fift, BoC)
- Automatic tests generation mode
- Custom checkers mode

```bash
$ java -jar tsa-cli.jar --help
Usage: ton-analysis [<options>] <command> [<args>]...

Options:
  -h, --help  Show this message and exit

Commands:
  tact            Options for analyzing Tact sources of smart contracts
  func            Options for analyzing FunC sources of smart contracts
  fift            Options for analyzing smart contracts in Fift assembler
  boc             Options for analyzing a smart contract in the BoC format
  test-gen        Options for test generation for FunC projects
  custom-checker  Options for using custom checkers
  inter-contract  Options for analyzing inter-contract communication of smart contracts
```

## [Runtime Errors Detection Guide](error-detection-mode)

The automated mode of TSA focuses on detecting errors in TON smart contracts, 
related to code implementation and causing the TON Virtual Machine (TVM) to crash during contract execution.

TVM runtime errors in TON smart contracts often arise from improper handling of data – 
primitives (numbers) and complex structures (slices, builders, dictionaries). 
The occurrence of [such errors](../error-types) makes it impossible to complete transactions, such as transferring funds, buying, or selling tokens, etc. 
The main mode of operation of TSA is to detect and reproduce such errors – if you are interested in this functionality, 
please refer to the [**Error Detection Guide**](error-detection-mode).

## [Automatic Blueprint Tests Generation Guide](test-gen-mode)

In test generation mode, `TSA` takes as input a project in the [Blueprint](https://github.com/ton-org/blueprint) format and
the relative path to the source code of the analyzed contract (as before, use `--help` argument for more detailed information about input format).

For more information about the test generation process, please refer to the [**Automatic Tests Generation Guide**](test-gen-mode).

## [Custom Checkers Guide](checking-mode)

Sometimes, errors in smart contracts are not related to runtime errors but to incorrect business logic – 
for example, the inability to transfer funds from a wallet under certain conditions. 
These issues are complex in nature but can often be discovered with custom checkers. 
The TSA Checking mode assists users in verifying both the business logic and required invariants of their own smart contracts, 
as well as checking the reliability of third-party contracts – if you are interested in this functionality,
please refer to the [**Custom Checkers Guide**](checking-mode).
