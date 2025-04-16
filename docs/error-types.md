---
layout: default
title: Detectors
nav_order: 4
---

# Error detectors

The primary types of [TVM runtime errors](https://docs.ton.org/v3/documentation/tvm/tvm-exit-codes) that occur in TON smart contracts can be divided into three categories:

- Arithmetic Errors
- Deserialization Errors (cell underflow)
- Serialization Errors (cell overflow)

## Arithmetic Errors

### Arithmetic Overflow

This type of error is represented in TVM by error code 4. It occurs during division by zero or when the result of an arithmetic operation exceeds 257 signed bits.

For instance, in the following function, overflows can occur in two places—within the `if` branch and during the return from the function in the `else` branch:

```c
(int) add(int second, int subtrahend, int flag) method_id {
    int first = 42;
    if (flag == -1) {
        second = second - subtrahend;
    } else {
        second = 0;
    }

    return first + second;
}
```

### Integer is Out of the Expected Range

This type of error, represented by TVM error code 5, occurs when specific instructions are used incorrectly, violating their argument constraints.

Such an error may arise, for example, when writing a number to a builder where the number’s bit length exceeds the expected size, as in the following function:

```c
(builder) write() method_id {
    builder b = begin_cell();
    b~store_uint(256, 4);

    return b;
}
```

## Deserialization Errors (Cell Underflow)

This type of error is represented by TVM error code 9 and occurs when reading more bits or references from a slice than it contains. It can occur during any `load_bits`, `load_int`, `load_ref`, or similar operations.

Since much of smart contract code involves reading incoming messages, this type of error can appear in nearly any smart contract—primarily because every TVM instruction has a gas cost, and most smart contracts do not perform safety checks before parsing. This issue could be mitigated using [TL-B schemes](https://docs.ton.org/v3/documentation/data-formats/tlb/tl-b-language); however:
1. Many smart contracts do not explicitly specify a TL-B scheme.
2. TL-B schemes are more of a specification than strict validation.

For example, in the following function, a cell underflow error is possible in the last instruction because there is no check to ensure the slice contains at least three necessary bits:

```c
(int) read(slice value, int flag) method_id {
    if (flag) {
        if (value.slice_bits() >= 4) {
            return value~load_uint(4);
        }

        return -1;
    }

    return value~load_int(3);
}
```

## Serialization Errors (Cell Overflow)

This type of error is represented by TVM error code 8 and occurs during improper writes to a builder, such as when the number of bits exceeds the 1023-bit limit or the number of references exceeds 4. Errors of this type can arise from any `store_bits`, `store_int`, `store_ref`, or similar operations.

Since outgoing message generation often follows a contract's business logic, this type of error can also occur in nearly any smart contract. Unlike [cell underflow](#deserialization-errors-cell-underflow), reliability in this aspect depends more on the developer’s intention. However, such errors can still originate from incoming messages when parameters are forwarded into outgoing messages.

A simple example of this type of error is the following function, where a reference overflow occurs if the `loop_count` parameter exceeds 4:

```c
(builder) write(int loop_count) method_id {
    builder b = begin_cell();

    if (loop_count < 0) {
        return b;
    }

    var i = 0;
    repeat(loop_count) {
        builder value = begin_cell().store_int(i, 32);
        b = b.store_ref(value.end_cell());
    }

    return b;
}
```
