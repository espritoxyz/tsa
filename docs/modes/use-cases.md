---
layout: default
title: Use cases
nav_order: 2
---

# Use cases

Before starting using TSA, ensure it is [installed](../installation) properly.

The primary purpose of TSA is to enhance the reliability of the TON blockchain and its smart contracts.
To achieve this goal, TSA can analyze trustworthy contracts in an automated mode (without user involvement) to identify errors,
or in a semi-automated mode (with user-defined custom checkers) to check smart contracts for vulnerabilities and reliability.

---

## [Runtime Errors Detection Guide](error-detection-mode)

The automated mode of TSA focuses on detecting errors in TON smart contracts, 
related to code implementation and causing the TON Virtual Machine (TVM) to crash during contract execution.

TVM runtime errors in TON smart contracts often arise from improper handling of data – 
[primitives (numbers)](../detectors#Arithmetic-Errors) and [complex structures](../detectors#Deserialization-Errors-Cell-Underflow) (slices, builders, dictionaries). 
The occurrence of such errors makes it impossible to complete transactions, such as transferring funds, buying, or selling tokens, etc. 

The main mode of operation of TSA is to detect and reproduce such errors – if you are interested in this functionality, 
please refer to the [**Runtime Error Detection Guide**](error-detection-mode).

---

## [Automatic Blueprint Tests Generation Guide](test-gen-mode)

In test generation mode, `TSA` takes as input a project in the [Blueprint](https://github.com/ton-org/blueprint) format and
the relative path to the source code of the analyzed contract (as before, use `--help` argument for more detailed information about input format).

For more information about the test generation process, please refer to the [**Automatic Blueprint Tests Generation Guide**](test-gen-mode).

---

## [Custom Checkers Step-by-step Guide](checking-mode)

Sometimes, errors in smart contracts are not related to runtime errors but to incorrect business logic – 
for example, the inability to transfer funds from a wallet under certain conditions. 
These issues are complex in nature but can often be discovered with custom checkers. 

The TSA Checking mode assists users in verifying both the business logic and required invariants of their own smart contracts, 
as well as checking the reliability of third-party contracts – if you are interested in this functionality,
please refer to the [**Custom Checkers Step-by-step Guide**](checking-mode).
