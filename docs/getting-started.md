To start using `TSA`, you can either use the prebuilt artifacts (depending on your operating system) or build the artifacts yourself from the source code.

## Running Prebuilt Artifacts

### Linux/MacOS

#### Using a Docker Container

```bash
docker run --platform linux/amd64 -it --rm -v [SOURCES_DIR_ABSOLUTE_PATH]:/project ghcr.io/espritoxyz/tsa:latest [ANALYZER_OPTIONS]
```

Here:

- `SOURCES_DIR_ABSOLUTE_PATH` – the absolute path to the directory containing the source code of the project you want to analyze;
- `ANALYZER_OPTIONS` – analyzer options (see [details](./error-checking-tests-generation-mode.md), or use the `--help` option).

**NOTE**: All paths in `ANALYZER_OPTIONS` must be RELATIVE to `SOURCES_DIR_ABSOLUTE_PATH`.

For example, to analyze inter-contract interactions between two FunC contracts located in `sender.fc` and `receiver.fc`, run the following command:

```bash
docker run --platform linux/amd64 -it --rm -v [SOURCES_DIR_ABSOLUTE_PATH]:/project ghcr.io/espritoxyz/tsa:latest inter /project/[FIRST_CONTRACT_RELATIVE_PATH] /project/[SECOND_CONTRACT_RELATIVE_PATH] --func-std /project/[PATH_TO_FUNC_STDLIB] --fift-std /project/[PATH_TO_FIFT_STDLIB_DIR]
```

#### Using JAR Executables

The [Releases page](https://github.com/espritoxyz/tsa/releases) provides two JAR executables:

- `tsa-cli.jar`
- `tsa-safety-properties.jar`

Before using them, ensure you have the following installed:

- [JRE](https://www.java.com/en/download/manual.jsp)
- [Tact compiler](https://github.com/tact-lang/tact)
- [FunC and Fift compilers](https://github.com/ton-blockchain/ton/releases/latest)

Then, you can run the analysis in the standard error-checking/tests generation mode:

```bash
java -jar tsa-cli.jar
```

or in the safety-properties checker mode:

```bash
java -jar tsa-safety-properties.jar
```

### Windows

Currently, `TSA` can only be run on Windows using the JAR executables. Refer to the [relevant section](#using-jar-executables) for details.

## Building from sources

1. Install all prerequisites:
   - At least `JDK 11` - any preferred build
   - [Gradle](https://gradle.org/)
   - [NodeJS](https://nodejs.org/en)
   - [Tact compiler](https://github.com/tact-lang/tact)
   - [FunC and Fift compilers](https://github.com/ton-blockchain/ton/releases/latest)
2. Clone this repository
3. Ensure `tact`, `func`, and `fift` are in your `$PATH`
4. Run `./gradlew tsa-cli:shadowJar` from the root of the project to build [error-checking analysis tool](./error-checking-tests-generation-mode.md) (will be located in [build dir](../tsa-cli/build/libs/tsa-cli.jar))
   or `./gradlew tsa-safety-properties:shadowJar` to build [safety-properties checker](./safety-properties-mode.md) (will be located in [build dir](/tsa-safety-properties/build/libs/tsa-safety-properties.jar))