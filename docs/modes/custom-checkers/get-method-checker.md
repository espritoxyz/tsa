---
layout: default
title: Checking the get method
parent: Custom checkers
nav_order: 1
---

# Checking the get method

This guide will walk you through implementing and running a custom checker to verify that a `naive_abs` method always returns a positive value.

---

## Step 1: Write the Contract

The `naive_abs` method is a simple implementation of an absolute value function. Copy the following code into your editor and save it as `abs.fc`:

```c
int naive_abs(int x) method_id(10) {
    if (x < 0) {
        return - x;
    } else {
        return x;
    }
}
```

This method is supposed to return the absolute value of the provided integer `x`.

---

## Step 2: Write the Checker

To verify the behavior of the `naive_abs` method, we will use the following checker. Copy this code into your editor and save it as `abs_checker.fc`:

```c
#include "../../imports/stdlib.fc";
#include "../../imports/tsa_functions.fc";

() recv_internal() impure {
    ;; Make a symbolic 257-bits signed integer
    int x = tsa_mk_int(257, -1);

    ;; Save this symbolic value to retrieve its concrete value in the result
    tsa_fetch_value(x, 0);

    ;; Call the naive_abs method
    int abs_value = tsa_call_1_1(x, 1, 10);

    ;; Actually, this exception is impossible to trigger as a negation triggers an integer overflow if x is INT_MIN
    throw_if(10, abs_value < 0);
}
```

This checker performs the following steps:
1. Creates a symbolic signed 257-bit integer value `x` using `tsa_mk_int`.
2. Saves this value to retrieve its concrete value in the result using `tsa_fetch_value`.
3. Calls the `naive_abs` method with the `x` value using `tsa_call_1_1`.
4. Throws an exception if the result of the `naive_abs` method is negative.

---

## Step 3: Run the Checker

To execute the checker, open your terminal and run the following command:

{% highlight bash %}
java -jar tsa-cli.jar custom-checker \
--checker tsa-safety-properties-examples/src/test/resources/examples/step1/abs_checker.fc \
--contract func tsa-safety-properties-examples/src/test/resources/examples/step1/abs.fc \
--func-std tsa-safety-properties-examples/src/test/resources/imports/stdlib.fc \
--fift-std tsa-safety-properties-examples/src/test/resources/fiftstdlib
{% endhighlight %}

This command will:
- Run the checker on the `abs.fc` contract.
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
                        "text": "TvmFailure(exit=TVM integer overflow, exit code: 4, type=UnknownError, phase=COMPUTE_PHASE)"
                    },
                    "properties": {
                        "gasUsage": 522,
                        "usedParameters": {
                            "type": "recvInternalInput",
                            "srcAddress": {
                                "cell": {
                                    "data": "100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
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
                        "fetchedValues": {
                            "0": "-115792089237316195423570985008687907853269984665640564039457584007913129639936"
                        },
                        "resultStack": [
                            "92233720368547758080",
                            "73786976294838206464",
                            {
                                "type": "org.usvm.test.resolver.TvmTestDataCellValue",
                                "data": "0000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100100000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001",
                                "refs": [
                                    {
                                        "type": "org.usvm.test.resolver.TvmTestDataCellValue"
                                    }
                                ]
                            }
                        ]
                    },
                    "ruleId": "integer-overflow"
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
1. The error message: `TvmFailure(exit=TVM integer overflow, exit code: 4, type=UnknownError, phase=COMPUTE_PHASE)` indicates an integer overflow.
2. The `fetchedValues` section shows the concrete value of `x` that caused the overflow: `-115792089237316195423570985008687907853269984665640564039457584007913129639936`.

This matches the behavior described in the `NEGATE` TVM instruction, which triggers an integer overflow when `x = -2^256`.

---
