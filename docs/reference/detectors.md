---
layout: default
title: Detectors
parent: Reference
nav_order: 1
---

# Error detectors

The primary types of [TVM runtime errors](https://docs.ton.org/v3/documentation/tvm/tvm-exit-codes#standard-exit-codes) 
that occur in TON smart contracts can be divided into three categories:

- [Arithmetic Errors](#arithmetic-errors)
- [Serialization Errors (cell overflow)](#8-cell-overflow)
- [Deserialization Errors (cell underflow)](#9-cell-underflow)

Besides that, TSA performs some additional checks, that do not correspond to the built-in TVM runtime errors.
For example, TSA can check the correctness of parsing slices with specified TL-B scheme.
For more detail, refer to the section about [additional checks](#additional-checks).

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

## Additional checks

### TL-B parsing errors

TSA might report the following errors about TL-B parsing:

- `unexpected-data-reading`: reading bits from slice when TL-B scheme suggests that it should have no bits left.
- `unexpected-ref-reading`: reading references from slice when TL-B scheme suggests that it should have no references left.
- `unexpected-end-of-cell`: calling `end_parse` when TL-B scheme suggests that the slice is not empty yet.
- `unexpected-cell-type`: reading unexpected data from slice. For example, calling `load_coins` on a slice when TL-B scheme suggests that it contains an address.
- `out-of-switch-bounds`. This error occurs when TL-B scheme suggests that the slice has a tag of the length `x`, but reading of a length greater than `x` is performed.
- `unexpected-type-for-switch`. This error occurs when TL-B scheme suggests that the reading of a tag is expected, but reading of unexpected type (for example, coins or address) is performed.

These checks can be turned off with the option `--no-tlb-checks`.

### Address parsing errors

These errors are about `addr_var` and anycast addresses, that [were forbidden in TVM 10](https://github.com/ton-blockchain/ton/blob/master/doc/GlobalVersions.md#anycast-addresses-and-address-rewrite).

Since behavior for such addresses are dependant from TVM version, these errors were moved separately from standard [cell underflow](#9-cell-underflow) errors.

Errors:

- `anycast-address-usage`: reading of an anycast address.
- `var-address-usage`: reading of `addr_var`.

### Dict operation errors

These errors occur when dictionary operations are performed on ordinary data cells and vice versa.

Errors:
- `dict-operation-on-data-cell`
- `data-cell-operation-on-dict`
