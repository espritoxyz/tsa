This module represents a built-in checker is used to implement a [validation](https://github.com/espritoxyz/tsa/blob/master/tsa-safety-properties/src/main/resources/checkers/symbolic_transfer.fc) of the [jetton-master](https://github.com/ton-blockchain/TEPs/blob/0d7989fba6f2d9cb08811bf47263a9b314dc5296/text/0074-jettons-standard.md#jetton-master-contract) contract
to ensure compliance with a corresponding [jetton-wallet specification](https://github.com/ton-blockchain/TEPs/blob/0d7989fba6f2d9cb08811bf47263a9b314dc5296/text/0074-jettons-standard.md#jetton-wallet-smart-contract).
Violations of this specification can lead to either incorrect smart contract behavior or even intentional vulnerabilities designed to scam users.
This checker accepts the address of a `jetton-master` contract as input and outputs a list of addresses to which token transfers are impossible.

## Build

<ol>
  <li>Firstly, install all prerequisites:
    <ul>
      <li>At least <code>JDK 11</code> - any preferred build</li>
      <li><a href="https://gradle.org/">Gradle</a></li>
    </ul>
</ol>

Then, build the JAR-based CLI for the checker using the following command from the root of the repository:

```bash
./gradlew :tsa-jettons:shadowJar
```

The resulting JAR file will be located in the `tsa-jettons/build/libs` directory.

## Usage

This checker accepts only one parameter – the address of the `jetton-master` contract to be analyzed.

```bash
$ java -jar tsa-jettons.jar --help
Usage: jetton-wallet-properties-analyzer [<options>]

Options:
  -a, --address=<text>  TON address of the jetton master contract.
  -h, --help            Show this message and exit
```

As a result, the checker will output a JSON object with the following fields:

```json
{
    "analyzedAddress": "address of the analyzed contract",
    "jettonWalletCodeHashBase64": "jetton wallet code hash in base64",
    "blacklistedAddresses": [
        "list of addresses to which token transfers are impossible"
    ]
}
```

## Example

Let’s consider using this checker with an example – the token [EQAyQ-wYe8U5hhWFtjEWsgyTFQYv1NYQiuoNz6H3L8tcPG3g](https://tonviewer.com/EQAyQ-wYe8U5hhWFtjEWsgyTFQYv1NYQiuoNz6H3L8tcPG3g), a scam token that cannot be resold after purchase.

Running the checker with the jetton-master address passed:

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
