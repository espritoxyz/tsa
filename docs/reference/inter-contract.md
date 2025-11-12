---
layout: default
title: Inter-contract analysis
parent: Reference
nav_order: 2
---

# Inter-contract Analysis

TSA allows to analyze intaraction of several contracts. This can be done in `custom-checker` or `inter-contract` modes.

If you want to use a message which was generated in some contract as an input of some other contract, you need to set **communication scheme**.
It is a JSON file that is passed with `--scheme` CLI option.

## Communication Scheme Format

TODO


### Example: scheme for jetton-wallet

Contract 1: jetton-wallet 1 owner.

Contract 2: jetton-wallet 1.

Contract 3: jetton-wallet 2.

Contract 4: jetton-wallet 2 owner.

```
[
  // Contract 1 sends all messages to contract 2
  {
    "id": 1,
    "inOpcodeToDestination": {},
    "other": {
      "type": "out_opcodes",
      "outOpcodeToDestination": {},
      "other": [2]
    }
  },
  {
    "id": 2,
    "inOpcodeToDestination": {
      "0f8a7ea5": {  // op::transfer
        "type": "out_opcodes",
        "outOpcodeToDestination": {
          "178d4519": [3]  // op::internal_transfer
        }
      }
    }
  },
  {
    "id": 3,
    "inOpcodeToDestination": {
      "178d4519": {  // op::internal_transfer
        "type": "out_opcodes",
        "outOpcodeToDestination": {
          "d53276db": [1],  // op::excesses
          "7362d09c": [4]  // op::transfer_notification
        }
      }
    }
  },
  // Contract 4 doesn't send any messages
  {
    "id": 4,
    "inOpcodeToDestination": {}
  }
]
```
