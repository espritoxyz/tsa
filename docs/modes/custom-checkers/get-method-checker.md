---
layout: default
title: Checking the get method
parent: Custom checkers
nav_order: 1
---

# Checking the get method

## Implementing a simple checker

Let's implement a simple conventional checker – verifying that an `abs` method always return a positive value.
A naive implementation of such a method could look like this in FunC (sources are available [abs.fc](https://github.com/espritoxyz/tsa/blob/74502fe3ba28c0b405dc8fe0904d466fe353a61c/tsa-safety-properties-examples/src/test/resources/examples/step1/abs.fc):

```c
int naive_abs(int x) method_id(10) {
    if (x < 0) {
        return - x;
    } else {
        return x;
    }
}
```

In more general languages (such as C or Java) this method returns a negative value for the `Int.MIN_VALUE`.
So, we could check is this behavior similar in TVM using the following checker (sources are available [abs_checker.fc](https://github.com/espritoxyz/tsa/blob/74502fe3ba28c0b405dc8fe0904d466fe353a61c/tsa-safety-properties-examples/src/test/resources/examples/step1/abs_checker.fc)):

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
    throw_if(-42, abs_value < 0);
}
```

This checker contains the following steps:
1. Create a symbolic signed 257-bits integer value `x` using `tsa_mk_int`.
2. Save this value to retrieve its concrete value in the result using `tsa_fetch_value`.
3. Call the `naive_abs` method with the `x` value provided using `tsa_call_1_1`.
4. Throw an exception if the result of the `naive_abs` method is negative.

## Running the checker

To run this checker, you need to have either the `tsa-cli.jar` JAR file downloaded/built or the Docker container pulled.
To make it more clear, let's use the JAR here – run this command from the root of the repository:

```bash
java -jar tsa-cli.jar custom-checker \
--checker tsa-safety-properties-examples/src/test/resources/examples/step1/abs_checker.fc \
--contract func tsa-safety-properties-examples/src/test/resources/examples/step1/abs.fc \
--func-std tsa-safety-properties-examples/src/test/resources/imports/stdlib.fc \
--fift-std tsa-safety-properties-examples/src/test/resources/fiftstdlib
```

This command will run the checker on the `abs.fc` contract and output the result in the SARIF format.

## Result

The result of the checker execution is a SARIF report that contains the following information:

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
                            "msgBody": {
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
                                "data": "0000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100100000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001",
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

We are interested in the following lines:
- `TvmFailure(exit=TVM integer overflow, exit code: 4, type=UnknownError, phase=COMPUTE_PHASE)`.
- `fetchedValues` part of the SARIF report that contains the concrete value of the `x` variable – `-115792089237316195423570985008687907853269984665640564039457584007913129639936`.

The first line reports that invoking the `naive_abs` may lead to an integer overflow,
and the second line gives the concrete value of the `x` variable that triggers this overflow – 
`-115792089237316195423570985008687907853269984665640564039457584007913129639936` that is the minimum value for a signed 257-bits integer.

This analyzer's report matches with a description of the `NEGATE` TVM instruction that is used in the `naive_abs` method implementation –
`Notice that it triggers an integer overflow exception if x=-2^256`.