To start using `TSA`, you can either use the prebuilt artifacts (depending on your operating system) or build the artifacts yourself from the source code.

## Running Prebuilt Artifacts

### Linux/MacOS

#### Using a Docker Container

{% highlight bash %}
docker run --platform linux/amd64 -it --rm -v [SOURCES_DIR_ABSOLUTE_PATH]:/project ghcr.io/espritoxyz/tsa:latest [ANALYZER_OPTIONS]
{% endhighlight %}

Here:

- `SOURCES_DIR_ABSOLUTE_PATH` – the absolute path to the directory containing the source code of the project you want to analyze;
- `ANALYZER_OPTIONS` – analyzer options (see [details](./error-checking-tests-generation-mode.md), or use the `--help` option).

**NOTE**: All paths in `ANALYZER_OPTIONS` must be RELATIVE to `SOURCES_DIR_ABSOLUTE_PATH`.

For example, to analyze inter-contract interactions between two FunC contracts located in `sender.fc` and `receiver.fc`, run the following command:

{% highlight bash %}
docker run --platform linux/amd64 -it --rm -v [SOURCES_DIR_ABSOLUTE_PATH]:/project ghcr.io/espritoxyz/tsa:latest inter /project/[FIRST_CONTRACT_RELATIVE_PATH] /project/[SECOND_CONTRACT_RELATIVE_PATH] --func-std /project/[PATH_TO_FUNC_STDLIB] --fift-std /project/[PATH_TO_FIFT_STDLIB_DIR]
{% endhighlight %}

#### Using JAR Executables

The [Releases page](https://github.com/espritoxyz/tsa/releases) provides a JAR executable:

- `tsa-cli.jar`

Before using it, ensure you have the following installed:

- [JRE](https://www.java.com/en/download/manual.jsp)
- [Tact compiler](https://github.com/tact-lang/tact)
- [FunC and Fift compilers](https://github.com/ton-blockchain/ton/releases/latest)

Then, you can run the analysis in the standard error-checking/tests generation mode or with checker mode:

{% highlight bash %}
java -jar tsa-cli.jar
{% endhighlight %}

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
4. Run `./gradlew tsa-cli:shadowJar` from the root of the project to build [error-checking analysis tool](modes/use-cases) (will be located in [build dir](../tsa-cli/build/libs/tsa-cli.jar))