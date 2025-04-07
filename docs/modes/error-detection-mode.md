---
layout: default
title: Errors detection mode
parent: Getting started
nav_order: 1
---

# Errors detection mode

As a static analyzer, `TSA` can operate in two modes: **runtime error detection** for local smart contracts with report generation in [SARIF format](https://sarifweb.azurewebsites.net/) or **test generation** for [Blueprint](https://github.com/ton-org/blueprint) projects.
For operating in this mode, use `tsa-cli.jar` or corresponding options in the Docker Container.

## Runtime Error Detection

In runtime error detection mode, `TSA` accepts as input a contract file in one of the following formats: Tact or FunC source code, or Fift assembler code, or BoC (compiled code). Optionally, it also accepts a [TL-B scheme](https://docs.ton.org/v3/documentation/data-formats/tlb/tl-b-language) for the `recv_internal` method (about TL-B schemes importance check [the internal design-document](../design/tlb)). For detailed input format information, use the `--help` argument. 

The output in this mode is a SARIF report containing the following information about methods that may encounter a [TVM error](https://docs.ton.org/v3/documentation/tvm/tvm-exit-codes) during execution:

- Instruction coverage percentage by the analyzer for the method (`coverage` field in the report)
- Method number (`decoratedName`) and TVM bytecode instruction(`stmt`) where the error may occur
- Error code and type (`text` in a `message`)
- Call stack `callFlows` (method id - instruction)
- Possible (but not necessarily unique) parameters set `usedParameters` causing the error
- Approximate gas usage `gasUsage` up to the error

For more information about error types, see the [relevant section](../error-types).

### Examples

Consider a simple smart contract that may encounter a cell overflow error when the `write` method receives a value greater than 4:

```c
#include "stdlib.fc";

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

() recv_internal(int msg_value, cell in_msg, slice in_msg_body) impure {
    ;; Do nothing
}
```

Running the analyzer for this contract with the following command 
(macOS ARM, assuming the contract, FunC and Fift stdlibs are located in the current directory):

```bash
docker run --platform linux/amd64 -it --rm -v $PWD:/project ghcr.io/espritoxyz/tsa:latest \ 
func -i /project/example.fc \
--func-std /project/stdlib.fc \
--fift-std /project/fiftstdlib
```

(please note that FunC stdlib is pointed using the specific option `func-std`, not as a part of the input file) identifies the error in the SARIF:

```json
{
    "$schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
    "version": "2.1.0",
    "runs": [
        {
            "properties": {
                "coverage": {
                    "0": 100.0,
                    "75819": 100.0
                }
            },
            "results": [
                {
                    "codeFlows": [
                        {
                            "threadFlows": [
                                {
                                    "locations": [
                                        {
                                            "location": {
                                                "logicalLocations": [
                                                    {
                                                        "decoratedName": "75819",
                                                        "properties": {
                                                            "stmt": "REPEAT#8"
                                                        }
                                                    }
                                                ]
                                            }
                                        }
                                    ]
                                }
                            ]
                        }
                    ],
                    "level": "error",
                    "locations": [
                        {
                            "logicalLocations": [
                                {
                                    "decoratedName": "75819"
                                }
                            ]
                        }
                    ],
                    "message": {
                        "text": "TvmFailure(exit=TVM integer out of expected range, exit code: 5, type=UnknownError)"
                    },
                    "properties": {
                        "gasUsage": 220,
                        "usedParameters": [
                            "2147483648"
                        ],
                        "resultStack": [
                            "0"
                        ]
                    },
                    "ruleId": "integer-out-of-range"
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

Here the analyzed method has the id `75819`, 
the analyzer covered 100% instructions of this method,
the `integer out of expected range` error with exit code `5` occurred in the stmt `8` in the `REPEAT` loop inside this method,
`2147483648` value passed to this method causes this error,
and gas usage before raising the error equals to `220`.

For more examples containing erroneous places, take a look at the directory in [the repository with manually written contracts](https://github.com/espritoxyz/tsa/tree/master/tsa-test/src/test/resources).
Feel free to run TSA by yourself for these contracts or consider [tests for them](https://github.com/espritoxyz/tsa/tree/master/tsa-test/src/test/kotlin/org/ton/examples).
