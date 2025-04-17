---
layout: default
title: Detectors
nav_order: 4
---

# Error detectors

The primary types of [TVM runtime errors](https://docs.ton.org/v3/documentation/tvm/tvm-exit-codes#standard-exit-codes) 
that occur in TON smart contracts can be divided into three categories:

- [Arithmetic Errors](#arithmetic-errors)
- [Serialization Errors (cell overflow)](#8-cell-overflow)
- [Deserialization Errors (cell underflow)](#9-cell-underflow)

---

## Arithmetic Errors

### [4: Integer Overflow](https://docs.ton.org/v3/documentation/tvm/tvm-exit-codes#4)

This type of error is represented in TVM by error code 4. It occurs during division by zero or when the result of an arithmetic operation exceeds 257 signed bits.
In real smart contracts, it can mostly occur when calculating gas values of working with incomes/outcomes for different balances.

### [5: Integer out of expected range](https://docs.ton.org/v3/documentation/tvm/tvm-exit-codes#5)

This type of error, represented by TVM error code 5, occurs when specific instructions are used incorrectly, violating their argument constraints.
In real smart contracts, this can happen when using the `repeat` instruction with an incorrect count or when using the storing functions with a values exceeding declared bit length.

---

## [De]serialization Errors

## [8: Cell overflow](https://docs.ton.org/v3/documentation/tvm/tvm-exit-codes#8)

This type of error is represented by TVM error code 8 and occurs during improper writes to a builder, 
such as when the number of bits exceeds the 1023-bit limit or the number of references exceeds 4. 
Errors of this type can arise from any `store_bits`, `store_int`, `store_ref`, or similar operations.

Since outgoing message generation often follows a contract's business logic, this type of error can also occur in nearly any smart contract. 
Unlike [cell underflow](#9-cell-underflow), reliability in this aspect depends more on the developer’s intention. However, such errors can still originate from incoming messages when parameters are forwarded into outgoing messages.

## [9: Cell underflow](https://docs.ton.org/v3/documentation/tvm/tvm-exit-codes#5)

This type of error is represented by TVM error code 9 and occurs when reading more bits or references from a slice than it contains. 
It can occur during any `load_bits`, `load_int`, `load_ref`, or similar operations.

Since much of smart contract code involves reading incoming messages, 
this type of error can appear in nearly any smart contract --- primarily because every TVM instruction has a gas cost, 
and most smart contracts do not perform safety checks before parsing. 
The issue could be partially solved using [TL-B schemes](https://docs.ton.org/v3/documentation/data-formats/tlb/tl-b-language), but this approach has some limitations:
1. Many smart contracts do not explicitly specify a TL-B scheme.
2. TL-B schemes are more of a specification than strict validation.