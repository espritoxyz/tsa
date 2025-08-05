---
layout: default
title: Errors detection mode
parent: Use cases
nav_order: 1
---

# Runtime errors detection mode

As a static analyzer, `TSA` can operate in two modes: 
- **Runtime error detection** for local smart contracts with report generation in [SARIF format](https://sarifweb.azurewebsites.net/);
- [**Test generation**](test-gen-mode) for [Blueprint](https://github.com/ton-org/blueprint) projects.

In runtime error detection mode, `TSA` accepts as input a contract file in one of the following formats:

<details>
    <summary><b>Tact source code</b></summary>

{% highlight bash %}
$ java -jar tsa-cli.jar tact --help
Usage: ton-analysis tact [<options>]

  Options for analyzing Tact sources of smart contracts

Contract properties:
  -d, --data=<text>  The serialized contract persistent data

SARIF options:
  -o, --output=<path>  The path to the output SARIF report file
  --no-user-errors     Do not report executions with user-defined errors

TlB scheme options:
  -t, --tlb=<path>  The path to the parsed TL-B scheme.
  --no-tlb-checks   Turn off TL-B parsing checks

Symbolic analysis options:
  --analyze-bounced-messages  Consider inputs when the message is bounced.
  --timeout=<int>             Analysis timeout in seconds.

Analysis target:

  What to analyze. By default, only receivers (recv_interval and recv_external)
  are analyzed.

  --method=<int>         Id of the method to analyze
  --analyze-receivers    Analyze recv_internal and recv_external (default)
  --analyze-all-methods  Analyze all methods (applicable only for contracts
                         with default main method)

Tact options:
  --tact=<text>  Tact executable. Default: tact

Options:
  -c, --config=<path>   The path to the Tact config (tact.config.json)
  -p, --project=<text>  Name of the Tact project to analyze
  -i, --input=<text>    Name of the Tact smart contract to analyze
  -h, --help            Show this message and exit
{% endhighlight %}
</details>

<details>
    <summary><b>FunC source code</b></summary>

{% highlight bash %}
$ java -jar tsa-cli.jar func --help
Usage: ton-analysis func [<options>]

  Options for analyzing FunC sources of smart contracts

Contract properties:
  -d, --data=<text>  The serialized contract persistent data

SARIF options:
  -o, --output=<path>  The path to the output SARIF report file
  --no-user-errors     Do not report executions with user-defined errors

TlB scheme options:
  -t, --tlb=<path>  The path to the parsed TL-B scheme.
  --no-tlb-checks   Turn off TL-B parsing checks

Symbolic analysis options:
  --analyze-bounced-messages  Consider inputs when the message is bounced.
  --timeout=<int>             Analysis timeout in seconds.

Analysis target:

  What to analyze. By default, only receivers (recv_interval and recv_external)
  are analyzed.

  --method=<int>         Id of the method to analyze
  --analyze-receivers    Analyze recv_internal and recv_external (default)
  --analyze-all-methods  Analyze all methods (applicable only for contracts
                         with default main method)

Fift options:
  --fift-std=<path>  The path to the Fift standard library (dir containing
                     Asm.fif, Fift.fif)

Options:
  -i, --input=<path>  The path to the FunC source of the smart contract
  -h, --help          Show this message and exit
{% endhighlight %}
</details>

  <details>
    <summary><b>Fift assembler code</b></summary>

{% highlight bash %}
$ java -jar tsa-cli.jar fift --help
Usage: ton-analysis fift [<options>]

  Options for analyzing smart contracts in Fift assembler

Contract properties:
  -d, --data=<text>  The serialized contract persistent data

SARIF options:
  -o, --output=<path>  The path to the output SARIF report file
  --no-user-errors     Do not report executions with user-defined errors

TlB scheme options:
  -t, --tlb=<path>  The path to the parsed TL-B scheme.
  --no-tlb-checks   Turn off TL-B parsing checks

Symbolic analysis options:
  --analyze-bounced-messages  Consider inputs when the message is bounced.
  --timeout=<int>             Analysis timeout in seconds.

Analysis target:

  What to analyze. By default, only receivers (recv_interval and recv_external)
  are analyzed.

  --method=<int>         Id of the method to analyze
  --analyze-receivers    Analyze recv_internal and recv_external (default)
  --analyze-all-methods  Analyze all methods (applicable only for contracts
                         with default main method)

Fift options:
  --fift-std=<path>  The path to the Fift standard library (dir containing
                     Asm.fif, Fift.fif)

Options:
  -i, --input=<path>  The path to the Fift assembly of the smart contract
  -h, --help          Show this message and exit
{% endhighlight %}
</details>

  <details>
    <summary><b>BoC (compiled code)</b></summary>
    
{% highlight bash %}
$ java -jar tsa-cli.jar boc --help 
Usage: ton-analysis boc [<options>]

  Options for analyzing a smart contract in the BoC format

Contract properties:
  -d, --data=<text>  The serialized contract persistent data

SARIF options:
  -o, --output=<path>  The path to the output SARIF report file
  --no-user-errors     Do not report executions with user-defined errors

TlB scheme options:
  -t, --tlb=<path>  The path to the parsed TL-B scheme.
  --no-tlb-checks   Turn off TL-B parsing checks

Symbolic analysis options:
  --analyze-bounced-messages  Consider inputs when the message is bounced.
  --timeout=<int>             Analysis timeout in seconds.

Analysis target:

  What to analyze. By default, only receivers (recv_interval and recv_external)
  are analyzed.

  --method=<int>         Id of the method to analyze
  --analyze-receivers    Analyze recv_internal and recv_external (default)
  --analyze-all-methods  Analyze all methods (applicable only for contracts
                         with default main method)

Options:
  -i, --input=<path>  The path to the smart contract in the BoC format
  -h, --help          Show this message and exit
{% endhighlight %}
</details>

By default, TSA analyzes only `recv_internal` and `recv_external`. To analyze a specific method, use `--method` option.

Optionally, it also accepts a [TL-B scheme](https://docs.ton.org/v3/documentation/data-formats/tlb/tl-b-language) for the `recv_internal` method. For detailed input format information, use the `--help` argument.
The output in this mode is a SARIF report containing the following information about methods that may encounter a [TVM error](https://docs.ton.org/v3/documentation/tvm/tvm-exit-codes) during execution:

- `coverage` field in the report - instruction coverage percentage by the analyzer for the method
- `decoratedName` and `inst` - the method id and instruction where the error may occur, correspondingly
- `text` in a `message` - error code and its type
- `usedParameters` - possible (but not necessarily unique) parameters set causing the error
  - The parameters might be returned in two formats: `recvInternalInput` and `stackInput`. The first one is used when analyzing `recv_internal` method.
- `gasUsage` - approximate gas usage of the execution when the error occurred

For more information about error types, see the [Detectors page](../detectors).

---

## Examples

NOTE: the original Tact and FunC compilers do not preserve source code location information (source maps) in the resulting compiled code,
and the SARIF report generated by the tool will not be able to pinpoint errors directly in the source code.

The position of the instruction where the failure happens is given in format `cell-hash + offset`, which is used in the [tasm tool](https://github.com/tact-lang/tasm/).

### Tact example

Consider a simple smart contract written in Tact that may encounter an arithmetic overflow error when the `divide` method receives a value of `subtrahend` close to the minimal integer value:

```javascript
contract Divider {
  init() {}
  receive() {}

  get fun subtractAndDivide(subtrahend: Int, dividend: Int, flag: Bool): Int {
    let divider: Int = 42;

    if (flag) {
      divider -= subtrahend;
    } else {
      divider = 2;
    }

    if (dividend == 100) {
      throw(100);
    }

    return dividend / divider;
  }
}
```

The method we want to analyze is `subtractAndDivide`, which has method id `127452`.

To eliminate executions that fail with user-defined exceptions, we also use `--no-user-errors` flag.

Running TSA for this contract:

{% highlight bash %}
java -jar tsa-cli.jar \
tact -c "tact.config.json" -p "sample" -i "Divider" --method 127452 --no-user-errors \
> Divider.sarif
{% endhighlight %}

produces a SARIF report.

<details>
  <summary><b>Raw SARIF report</b></summary>

{% highlight json %}
{
    "$schema": "https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json",
    "version": "2.1.0",
    "runs": [
        {
            "properties": {
                "coverage": {
                    "127452": 100.0
                }
            },
            "results": [
                {
                    "level": "error",
                    "locations": [
                        {
                            "logicalLocations": [
                                {
                                    "decoratedName": "127452",
                                    "properties": {
                                        "position": {
                                            "cellHashHex": "91FC6E7B5CEC4435584AD4A60E936B9971A56CCC3B302F5B8CE2CD88D4E88833",
                                            "offset": 24
                                        },
                                        "inst": "LDI"
                                    }
                                }
                            ]
                        }
                    ],
                    "message": {
                        "text": "TvmFailure(exit=TVM cell underflow, exit code: 9, type=FixedStructuralError, phase=COMPUTE_PHASE)"
                    },
                    "properties": {
                        "gasUsage": 306,
                        "usedParameters": {
                            "type": "stackInput",
                            "usedParameters": [
                            ]
                        },
                        "rootContractInitialC4": {
                            "type": "org.usvm.test.resolver.TvmTestDataCellValue",
                            "knownTypes": [
                                {
                                    "type": {
                                        "type": "org.usvm.test.resolver.TvmTestCellDataIntegerRead",
                                        "bitSize": 1,
                                        "isSigned": true,
                                        "endian": "BigEndian"
                                    },
                                    "offset": 0
                                }
                            ]
                        },
                        "resultStack": [
                            "0"
                        ]
                    },
                    "ruleId": "cell-underflow"
                },
                {
                    "level": "error",
                    "locations": [
                        {
                            "logicalLocations": [
                                {
                                    "decoratedName": "127452",
                                    "properties": {
                                        "position": {
                                            "cellHashHex": "15A117B18C56A6869BB2D4DC8756972F73922F336788916F7AEA4898CC2E2A25",
                                            "offset": 40
                                        },
                                        "inst": "SUB"
                                    }
                                }
                            ]
                        }
                    ],
                    "message": {
                        "text": "TvmFailure(exit=TVM integer overflow, exit code: 4, phase=COMPUTE_PHASE)"
                    },
                    "properties": {
                        "gasUsage": 687,
                        "usedParameters": {
                            "type": "stackInput",
                            "usedParameters": [
                                "-115792089237316195423570985008687907853269984665640564039457584007913129639894",
                                "1"
                            ]
                        },
                        "rootContractInitialC4": {
                            "type": "org.usvm.test.resolver.TvmTestDataCellValue",
                            "data": "0",
                            "refs": [
                                {
                                    "type": "org.usvm.test.resolver.TvmTestDataCellValue"
                                }
                            ],
                            "knownTypes": [
                                {
                                    "type": {
                                        "type": "org.usvm.test.resolver.TvmTestCellDataIntegerRead",
                                        "bitSize": 1,
                                        "isSigned": true,
                                        "endian": "BigEndian"
                                    },
                                    "offset": 0
                                }
                            ]
                        },
                        "resultStack": [
                            "0"
                        ]
                    },
                    "ruleId": "integer-overflow"
                },
                {
                    "level": "error",
                    "locations": [
                        {
                            "logicalLocations": [
                                {
                                    "decoratedName": "127452",
                                    "properties": {
                                        "position": {
                                            "cellHashHex": "15A117B18C56A6869BB2D4DC8756972F73922F336788916F7AEA4898CC2E2A25",
                                            "offset": 136
                                        },
                                        "inst": "DIV"
                                    }
                                }
                            ]
                        }
                    ],
                    "message": {
                        "text": "TvmFailure(exit=TVM integer overflow, exit code: 4, phase=COMPUTE_PHASE)"
                    },
                    "properties": {
                        "gasUsage": 796,
                        "usedParameters": {
                            "type": "stackInput",
                            "usedParameters": [
                                "42",
                                "101",
                                "1"
                            ]
                        },
                        "rootContractInitialC4": {
                            "type": "org.usvm.test.resolver.TvmTestDataCellValue",
                            "data": "0",
                            "refs": [
                                {
                                    "type": "org.usvm.test.resolver.TvmTestDataCellValue"
                                },
                                {
                                    "type": "org.usvm.test.resolver.TvmTestDataCellValue"
                                }
                            ],
                            "knownTypes": [
                                {
                                    "type": {
                                        "type": "org.usvm.test.resolver.TvmTestCellDataIntegerRead",
                                        "bitSize": 1,
                                        "isSigned": true,
                                        "endian": "BigEndian"
                                    },
                                    "offset": 0
                                }
                            ]
                        },
                        "resultStack": [
                            "0"
                        ]
                    },
                    "ruleId": "integer-overflow"
                },
                {
                    "level": "error",
                    "locations": [
                        {
                            "logicalLocations": [
                                {
                                    "decoratedName": "127452",
                                    "properties": {
                                        "position": {
                                            "cellHashHex": "15A117B18C56A6869BB2D4DC8756972F73922F336788916F7AEA4898CC2E2A25",
                                            "offset": 136
                                        },
                                        "inst": "DIV"
                                    }
                                }
                            ]
                        }
                    ],
                    "message": {
                        "text": "TvmFailure(exit=TVM integer overflow, exit code: 4, phase=COMPUTE_PHASE)"
                    },
                    "properties": {
                        "gasUsage": 796,
                        "usedParameters": {
                            "type": "stackInput",
                            "usedParameters": [
                                "43",
                                "-115792089237316195423570985008687907853269984665640564039457584007913129639936",
                                "1"
                            ]
                        },
                        "rootContractInitialC4": {
                            "type": "org.usvm.test.resolver.TvmTestDataCellValue",
                            "data": "0",
                            "refs": [
                                {
                                    "type": "org.usvm.test.resolver.TvmTestDataCellValue"
                                },
                                {
                                    "type": "org.usvm.test.resolver.TvmTestDataCellValue"
                                },
                                {
                                    "type": "org.usvm.test.resolver.TvmTestDataCellValue"
                                },
                                {
                                    "type": "org.usvm.test.resolver.TvmTestDataCellValue"
                                }
                            ],
                            "knownTypes": [
                                {
                                    "type": {
                                        "type": "org.usvm.test.resolver.TvmTestCellDataIntegerRead",
                                        "bitSize": 1,
                                        "isSigned": true,
                                        "endian": "BigEndian"
                                    },
                                    "offset": 0
                                }
                            ]
                        },
                        "resultStack": [
                            "0"
                        ]
                    },
                    "ruleId": "integer-overflow"
                },
                {
                    "level": "error",
                    "locations": [
                        {
                            "logicalLocations": [
                                {
                                    "decoratedName": "127452",
                                    "properties": {
                                        "position": {
                                            "cellHashHex": "15A117B18C56A6869BB2D4DC8756972F73922F336788916F7AEA4898CC2E2A25",
                                            "offset": 40
                                        },
                                        "inst": "SUB"
                                    }
                                }
                            ]
                        }
                    ],
                    "message": {
                        "text": "TvmFailure(exit=TVM integer overflow, exit code: 4, phase=COMPUTE_PHASE)"
                    },
                    "properties": {
                        "gasUsage": 687,
                        "usedParameters": {
                            "type": "stackInput",
                            "usedParameters": [
                                "-115792089237316195423570985008687907853269984665640564039457584007913129639894",
                                "1"
                            ]
                        },
                        "rootContractInitialC4": {
                            "type": "org.usvm.test.resolver.TvmTestDataCellValue",
                            "data": "1",
                            "knownTypes": [
                                {
                                    "type": {
                                        "type": "org.usvm.test.resolver.TvmTestCellDataIntegerRead",
                                        "bitSize": 1,
                                        "isSigned": true,
                                        "endian": "BigEndian"
                                    },
                                    "offset": 0
                                }
                            ]
                        },
                        "resultStack": [
                            "0"
                        ]
                    },
                    "ruleId": "integer-overflow"
                },
                {
                    "level": "error",
                    "locations": [
                        {
                            "logicalLocations": [
                                {
                                    "decoratedName": "127452",
                                    "properties": {
                                        "position": {
                                            "cellHashHex": "15A117B18C56A6869BB2D4DC8756972F73922F336788916F7AEA4898CC2E2A25",
                                            "offset": 136
                                        },
                                        "inst": "DIV"
                                    }
                                }
                            ]
                        }
                    ],
                    "message": {
                        "text": "TvmFailure(exit=TVM integer overflow, exit code: 4, phase=COMPUTE_PHASE)"
                    },
                    "properties": {
                        "gasUsage": 796,
                        "usedParameters": {
                            "type": "stackInput",
                            "usedParameters": [
                                "43",
                                "-115792089237316195423570985008687907853269984665640564039457584007913129639936",
                                "1"
                            ]
                        },
                        "rootContractInitialC4": {
                            "type": "org.usvm.test.resolver.TvmTestDataCellValue",
                            "data": "1",
                            "knownTypes": [
                                {
                                    "type": {
                                        "type": "org.usvm.test.resolver.TvmTestCellDataIntegerRead",
                                        "bitSize": 1,
                                        "isSigned": true,
                                        "endian": "BigEndian"
                                    },
                                    "offset": 0
                                }
                            ]
                        },
                        "resultStack": [
                            "0"
                        ]
                    },
                    "ruleId": "integer-overflow"
                },
                {
                    "level": "error",
                    "locations": [
                        {
                            "logicalLocations": [
                                {
                                    "decoratedName": "127452",
                                    "properties": {
                                        "position": {
                                            "cellHashHex": "15A117B18C56A6869BB2D4DC8756972F73922F336788916F7AEA4898CC2E2A25",
                                            "offset": 136
                                        },
                                        "inst": "DIV"
                                    }
                                }
                            ]
                        }
                    ],
                    "message": {
                        "text": "TvmFailure(exit=TVM integer overflow, exit code: 4, phase=COMPUTE_PHASE)"
                    },
                    "properties": {
                        "gasUsage": 796,
                        "usedParameters": {
                            "type": "stackInput",
                            "usedParameters": [
                                "42",
                                "0",
                                "1"
                            ]
                        },
                        "rootContractInitialC4": {
                            "type": "org.usvm.test.resolver.TvmTestDataCellValue",
                            "data": "1",
                            "knownTypes": [
                                {
                                    "type": {
                                        "type": "org.usvm.test.resolver.TvmTestCellDataIntegerRead",
                                        "bitSize": 1,
                                        "isSigned": true,
                                        "endian": "BigEndian"
                                    },
                                    "offset": 0
                                }
                            ]
                        },
                        "resultStack": [
                            "0"
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
{% endhighlight %}
</details>

The first error occurrs due to incorrecly initialized C4. To eliminate such errors, set cocrete C4 with option `--data`.

The other errors show different possible integer overflows in such contract.

---

### FunC example

Consider a simple smart contract that may encounter a cell overflow error when the `write` method receives a value greater than 4:

```c
#include "stdlib.fc";

(builder) write(int loop_count) method_id {
    builder b = begin_cell();

    if (loop_count < 0) {
        return b;
    }
    
    ;; Ensure loop count is in range [-2^31;2^31] for the repeat loop
    loop_count = loop_count & 0x111111;
    var i = 0;
    repeat(loop_count) {
        builder value = begin_cell().store_int(i, 32);

        b = b.store_ref(value.end_cell());
    }

    return b;
}

() recv_internal(int msg_value, cell in_msg, slice in_msg_body) impure {
    int loop_count = in_msg_body~load_int(32);
    builder value = write(loop_count);
    set_data(value.end_cell());
}
```

Running the analyzer for this contract with the following command 
(assuming the contract, FunC and Fift stdlibs are located in the current directory):

{% highlight bash %}
java -jar tsa-cli.jar \
func -i /project/example.fc --fift-std /project/fiftstdlib --method 0 \
> CellOverflow.sarif
{% endhighlight %}

identifies the error in the raw SARIF report:

<details>
  <summary><b>Raw SARIF report</b></summary>

{% highlight json %}
{
    "$schema": "https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json",
    "version": "2.1.0",
    "runs": [
        {
            "properties": {
                "coverage": {
                    "0": 100.0
                }
            },
            "results": [
                {
                    "level": "error",
                    "locations": [
                        {
                            "logicalLocations": [
                                {
                                    "decoratedName": "0",
                                    "properties": {
                                        "position": {
                                            "cellHashHex": "D61895B014C3C65BD3848270B17ACC2CD2A6AE3889C8D0E8BB51D862EBF18576",
                                            "offset": 16
                                        },
                                        "inst": "LDI"
                                    }
                                }
                            ]
                        }
                    ],
                    "message": {
                        "text": "TvmFailure(exit=TVM cell underflow, exit code: 9, type=FixedStructuralError, phase=COMPUTE_PHASE)"
                    },
                    "properties": {
                        "gasUsage": 188,
                        "usedParameters": {
                            "type": "recvInternalInput",
                            "srcAddress": {
                                "cell": {
                                    "data": "100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
                                }
                            },
                            "msgBody": {
                                "cell": {
                                    "knownTypes": [
                                        {
                                            "type": {
                                                "type": "org.usvm.test.resolver.TvmTestCellDataIntegerRead",
                                                "bitSize": 32,
                                                "isSigned": true,
                                                "endian": "BigEndian"
                                            },
                                            "offset": 0
                                        }
                                    ]
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
                        "rootContractInitialC4": {
                            "type": "org.usvm.test.resolver.TvmTestDataCellValue"
                        },
                        "resultStack": [
                            "0"
                        ]
                    },
                    "ruleId": "cell-underflow"
                },
                {
                    "level": "error",
                    "locations": [
                        {
                            "logicalLocations": [
                                {
                                    "decoratedName": "0",
                                    "properties": {
                                        "position": {
                                            "cellHashHex": "FB03ED4CCA61DC70C17C9A270289101970E900837BA0F3EEABB0DD5D91CEA5D3",
                                            "offset": 184
                                        },
                                        "inst": "STREF"
                                    }
                                }
                            ]
                        }
                    ],
                    "message": {
                        "text": "TvmFailure(exit=TVM cell overflow, exit code: 8, phase=COMPUTE_PHASE)"
                    },
                    "properties": {
                        "gasUsage": 3760,
                        "usedParameters": {
                            "type": "recvInternalInput",
                            "srcAddress": {
                                "cell": {
                                    "data": "100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
                                }
                            },
                            "msgBody": {
                                "cell": {
                                    "data": "00000000000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                                    "knownTypes": [
                                        {
                                            "type": {
                                                "type": "org.usvm.test.resolver.TvmTestCellDataIntegerRead",
                                                "bitSize": 32,
                                                "isSigned": true,
                                                "endian": "BigEndian"
                                            },
                                            "offset": 0
                                        }
                                    ]
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
                        "rootContractInitialC4": {
                            "type": "org.usvm.test.resolver.TvmTestDataCellValue"
                        },
                        "resultStack": [
                            "0"
                        ]
                    },
                    "ruleId": "cell-overflow"
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
{% endhighlight %}
</details>

Here we analyzed `recv_internal`, which has method id `0`. The analyzer covered 100% instructions.

The first error occurrs when the given message body is too short. To eliminate such errors, specify TL-B scheme for the input data.

Another error is the expected cell overflow.

Since we analyzed `recv_internal`, the input is given not as raw stack elements, but as parsed message data, including fields like `msgValue`, `bounced` and other.

For more examples containing erroneous places, take a look at the directory in [the repository with manually written contracts](https://github.com/espritoxyz/tsa/tree/master/tsa-test/src/test/resources).
