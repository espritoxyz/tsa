---
layout: default
title: Installation
nav_order: 3
---

# Installation
{: .no_toc }

To start using `TSA`, you can either use the prebuilt artifacts (depending on your operating system) or build the artifacts yourself from the source code.

---

## Running Prebuilt Artifacts

### Using a Docker Container

{% highlight bash %}
docker run --platform linux/amd64 -it --rm \
-v [SOURCES_DIR_ABSOLUTE_PATH]:/project \
ghcr.io/espritoxyz/tsa:latest \
[ANALYZER_OPTIONS]
{% endhighlight %}

Here:

- `SOURCES_DIR_ABSOLUTE_PATH` – the absolute path to the directory containing the source code of the project you want to analyze;
- `ANALYZER_OPTIONS` – analyzer options (see [the Use Cases Guide](modes/use-cases), or use the `--help` option).

<div class="note" style="border: 1px solid #ccc; padding: 10px; border-radius: 5px;">
  <strong>NOTE:</strong> All paths must be RELATIVE to the <code>project</code> dir.
</div>

<div class="note" style="border: 1px solid #ccc; padding: 10px; border-radius: 5px;">
  <strong>NOTE:</strong> All paths in <code>ANALYZER_OPTIONS</code> must be RELATIVE to <code>SOURCES_DIR_ABSOLUTE_PATH</code>.
</div>

<div class="note" style="border: 1px solid #ccc; padding: 10px; border-radius: 5px;">
  <strong>NOTE:</strong> Use <a href="https://learn.microsoft.com/en-us/windows/wsl/install" target="_blank">WSL</a> on the Windows machine.
</div>

---

### Using JAR Executables

The [Releases page](https://github.com/espritoxyz/tsa/releases/latest) provides a JAR executable `tsa-cli.jar`.

Before using it, ensure you have the following installed:

- [JRE](https://www.java.com/en/download/manual.jsp)
- [Tact compiler](https://github.com/tact-lang/tact) (only if you want to analyze Tact sources)
- [FunC and Fift compilers](https://github.com/ton-blockchain/ton/releases/latest) (if you want to analyze FunC or Fift sources)

Then, you can access the tool by running the JAR executable from the command line:

{% highlight bash %}
java -jar tsa-cli.jar
{% endhighlight %}

---

## Building from sources

<ol>
  <li>Install all prerequisites:
    <ul>
      <li>At least <code>JDK 11</code> - any preferred build</li>
      <li><a href="https://gradle.org/">Gradle</a></li>
      <li><a href="https://nodejs.org/en">NodeJS</a></li>
      <li><a href="https://github.com/tact-lang/tact">Tact compiler</a></li>
      <li><a href="https://github.com/ton-blockchain/ton/releases/latest">FunC and Fift compilers</a></li>
    </ul>
  </li>
  <li>Clone the repository 
   <br> 
   {% highlight bash %} git clone https://github.com/espritoxyz/tsa/ {% endhighlight %}
   </li>
  <li>Run
   <br>
   {% highlight bash %} ./gradlew tsa-cli:shadowJar {% endhighlight %}
   from the root of the project to build the tool JAR executable (will be located in the build dir of the <code>tsa-cli</code> module)</li>
</ol>

