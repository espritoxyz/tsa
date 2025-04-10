TSA(TON Symbolic Analyzer) is a static analysis tool based on symbolic execution and designed for smart contracts on the [TON blockchain](https://ton.org/).

### Quick start

To start using TSA, follow the [guide](docs/installation).

To know more about the core design, read the [corresponding documents](docs/design).

### Language Support
TSA works on TVM bitcode level, so it is possible to analyze smart contracts written in any language, just need to compile it to BoC format.

### Use Cases
TSA is designed for a few purposes:

- Detect possible TVM runtime errors: Find possible misbehavior while processing integers (overflow/underflow, division by zero) and slices/builders (reading or writing wrong number of bits, incorrect types, etc).
- Generate regression tests: TSA is able to generate Blueprint-based tests based on discovered execution paths that allow to fix expected behavior and find errorneous executions.
- Honeypots detection: TSA can detect and report malicious contracts that are created to fool users.

### Funding
TSA has been funded by the [TON Foundation grant](https://github.com/ton-society/grants-and-bounties/issues/489) grant and has been developed under the [8-month roadmap](https://questbook.app/dashboard/?proposalId=667ee6b9b59d3e9ae042d6c9&chainId=10&role=builder&isRenderingProposalBody=true&grantId=65c7836df27e2e1702d2d279).

### Inspiration

TSA is inspired and is actively using the [Universal Symbolic Virtual Machine(USVM)](https://github.com/UnitTestBot/usvm) –
a symbolic core engine for multiple programming languages.

USVM and TSA itself also widely use the [KSMT](https://github.com/UnitTestBot/ksmt) library –
a Kotlin/Java API for SMT solvers, with some optimizations for TON blockchain.

You are very welcome to contribute to both of these projects.
