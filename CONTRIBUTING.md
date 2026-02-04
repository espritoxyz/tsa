# Contributing to TSA

GitHub CI builds, tests and performs linter checks on the project.

## How to build CLI tool

1. Clone this repo (using `IntelliJ Idea` or `git clone https://github.com/espritoxyz/tsa`).
2. Make sure you have JDK installed (for running CLI tool you only need JRE): `javac --version`.
3. Build the CLI tool, running `./gradlew :tsa-cli:shadowJar` (for Unix) or `./gradlew.bat :tsa-cli:shadowJar` (for Windows) from the root of the repo.
The result will be located in `tsa-cli/build/libs/tsa-cli.jar`.

## How to run tests

Running tests requires some special environment.

1. Install `nodejs` and `npm`. [Installation guide](https://docs.npmjs.com/downloading-and-installing-node-js-and-npm).
2. Download `fift` and `func` for the corresponding operating system from [the last TON release ](https://github.com/ton-blockchain/ton/releases/) and add them to `$PATH`.
    - rename the downloaded files (e.g., `func-mac-arm64`, `fift-mac-arm64`)  to `fift` and `func`.
    - if you have `Permission denied` error, do `chmod +x fift` and `chmod +x func`
    - on MacOS you need to manually open these files once
3. Install `tact` compiler with [yarn](https://classic.yarnpkg.com/lang/en/docs/install):
   ```bash
   yarn global add @tact-lang/compiler
   ```
   - You will probably need to manually add `tact` to path:
       ```bash
       export PATH="$PATH:$(yarn global bin)"
       ```

4. Run tests with `./gradlew test` (for Unix) or `./gradlew.bat test` (for Windows) from the root of the repo.

### Hard tests

By default, only quick tests are run. Such check takes around 10 minutes. To run all tests, set environment variable `TSA_RUN_HARD_TESTS` to non-empty value. Full check takes more than an hour to execute.

## Linter and formatter

Before contributing, you must run formatter and linter with `./gradlew formatAndLintAll` (for Unix) or `./gradlew.bat formatAndLintAll` (for Windows) from the root of the repo.

Some format issues will be fixed automatically, some will require manual fixes.
