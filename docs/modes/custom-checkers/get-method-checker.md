---
layout: default
title: Checking the get method
parent: Checking mode
nav_order: 1
---

# Checking the get method

This guide will walk you through implementing and running a custom checker to verify that a `sort_pair` method correctly orders a pair of integers.

---

## Step 1: Write the Contract

The `sort_pair` method is a simple implementation that sorts two integers. Copy the following code into your editor and save it as `sort.fc`:

```c
(int, int) sort_pair(int x, int y) method_id(10) {
    if (x < y) {
         return (x, y);
    } else {
         return (x, y);
    }
}
```

This method is supposed to return the two integers in ascending order.

---

## Step 2: Write the Checker

To verify the behavior of the `sort_pair` method, we will use the following checker. Copy this code into your editor and save it as `sort_checker.fc`:

```c
#include "../../imports/stdlib.fc";
#include "../../imports/tsa_functions.fc";

() recv_internal() impure {
    ;; Make two symbolic 257-bits signed integers
    int x = tsa_mk_int(257, -1);
    int y = tsa_mk_int(257, -1);

    ;; Save these symbolic values by indices 0 and 1 to retrieve their concrete values in the result
    tsa_fetch_value(x, 0);
    tsa_fetch_value(y, 1);

    ;; Call the sort_pair method – the method with id 10 in the contract with its id 1 (id 0 is used for the checker)
    (int a, int b) = tsa_call_2_2(x, y, 1, 10);

    ;; Throw if the first value is greater than the second one
    throw_if(256, a > b);
}
```

This checker performs the following steps:
1. Creates two symbolic signed 257-bit integer values `x` and `y` using `tsa_mk_int`.
2. Saves these values to retrieve their concrete values in the result using `tsa_fetch_value`.
3. Calls the `sort_pair` method with the `x` and `y` values using `tsa_call_2_2`.
4. Throws an exception if the first returned value is greater than the second one, which would indicate that the pair is not correctly sorted.

---

## Step 3: Run the Checker

To execute the checker, open your terminal and run the following command:

{% highlight bash %}
java -jar tsa-cli.jar custom-checker \
--checker tsa-safety-properties-examples/src/test/resources/examples/step1/sort_checker.fc \
--contract func tsa-safety-properties-examples/src/test/resources/examples/step1/sort.fc \
--func-std tsa-safety-properties-examples/src/test/resources/imports/stdlib.fc \
--fift-std tsa-safety-properties-examples/src/test/resources/fiftstdlib
{% endhighlight %}

This command will:
- Run the checker on the `sort.fc` contract.
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
                        "text": "TvmFailure(exit=TVM user defined error with exit code 256, type=UnknownError, phase=COMPUTE_PHASE)"
                    },
                    "properties": {
                        "gasUsage": 675,
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
                            "0": "42",
                            "1": "13"
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

We are interested in lines with the following indices:
- `10` - the error message: `TvmFailure(exit=TVM user defined error with exit code 256, type=UnknownError, phase=COMPUTE_PHASE)` indicates that our checker's condition failed.
- `31` - the `fetchedValues` section shows the concrete values of `x=42` and `y=13` that caused the failure.

This result indicates that when `x=42` and `y=13`, the `sort_pair` method returned the values in the wrong order, with the larger value first, which triggered our checker's exception.

---
