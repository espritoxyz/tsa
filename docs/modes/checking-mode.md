---
layout: default
title: Checking mode
parent: Getting started
nav_order: 3
---

# Checking mode

A more advanced mode of `TSA` operation is the **checking mode**. `TSA` provides a [set of special functions in FunC](https://github.com/espritoxyz/tsa/blob/74502fe3ba28c0b405dc8fe0904d466fe353a61c/tsa-safety-properties-examples/src/test/resources/imports/tsa_functions.fc) with specific meanings for the analyzer. 
These include contract method invocation functions ([`tsa_call_*_*`](../design/tsa-checker-functions)), functions to enable/disable error detection (`tsa_forbid_failures`/`tsa_allow_failures`), and symbolic condition assertion functions (`tsa_assert`/`tsa_assert_not`).
These functions allow the configuration of the analyzer to validate specific contract specifications.

## [**Custom Checkers**](custom-checkers/custom-checkers)

About implementing your own checkers for the checking mode (with some examples provided),
see the [**Step-by-step guide**](custom-checkers/custom-checkers).

## Example - checking jetton wallets
 
Currently, a built-in checker is used to implement a [validation](https://github.com/espritoxyz/tsa/blob/master/tsa-safety-properties/src/main/resources/checkers/symbolic_transfer.fc) of the [jetton-master](https://github.com/ton-blockchain/TEPs/blob/master/text/0074-jettons-standard#jetton-master-contract) contract to ensure compliance with a [specification](https://github.com/ton-blockchain/TEPs/blob/master/text/0074-jettons-standard) of the corresponding `jetton-wallet`. 
Violations of this specification can lead to either incorrect smart contract behavior or even intentional vulnerabilities designed to exploit users. 
This checker is represented as a [separate module](https://github.com/espritoxyz/tsa/blob/74502fe3ba28c0b405dc8fe0904d466fe353a61c/tsa-jettons) with its own CLI tha accepts the address of a `jetton-master` contract as input and outputs a list of addresses to which token transfers are impossible.

Let’s consider using this checker with an example – the token [EQAyQ-wYe8U5hhWFtjEWsgyTFQYv1NYQiuoNz6H3L8tcPG3g](https://tonviewer.com/EQAyQ-wYe8U5hhWFtjEWsgyTFQYv1NYQiuoNz6H3L8tcPG3g), a scam token that cannot be resold after purchase. 

Firstly, build the JAR-based CLI for the checker using the following command from the root of the repository:

```bash
./gradlew :tsa-jettons:shadowJar
```

Then this JAR is available in the `tsa-jettons/build/libs` directory.
Run it on this contract with the following command (ensure you are in the correct directory):

```bash
java -jar tsa-jettons.jar -a EQAyQ-wYe8U5hhWFtjEWsgyTFQYv1NYQiuoNz6H3L8tcPG3g
```

returns the following output:

```json
{
    "analyzedAddress": "EQAyQ-wYe8U5hhWFtjEWsgyTFQYv1NYQiuoNz6H3L8tcPG3g",
    "jettonWalletCodeHashBase64": "peaXBR8Ky/bgTbDDlWZHq9VS7ssYwHMFYXIRusEhmcc=",
    "blacklistedAddresses": [
        "0111011110011101110011001000000101010001001110001101100101010000000011100100010010011100010100101001000111100111111100010010011100111000110000100011110101010111010110110101001100010000000000000000111101101010001001010011101111010110000001110011100001001110",
        "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
    ]
}
```

The first address in this list corresponds to the [STON.fi exchange router](https://ston.fi/), where the token is hosted. This indicates that the token cannot be sold after purchase, confirming it as a scam token.
