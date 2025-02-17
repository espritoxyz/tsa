FROM ubuntu:20.04

ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y -q --no-install-recommends ca-certificates curl wget apt-transport-https gpg git unzip \
    && curl -fsSL -O https://github.com/ton-blockchain/packages/releases/latest/download/ton-linux-x86-64.tar.gz \
    && mkdir -p ton-linux && tar -xf ton-linux-x86-64.tar.gz -C ton-linux \
    && cp -r ton-linux/* /usr/ \
    && rm -rf ton-linux ton-linux-x86-64.tar.gz

RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y nodejs

RUN apt-get update --fix-missing

RUN wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null \
    && echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list \
    && apt update && apt install -y -q --no-install-recommends temurin-17-jdk \
    && rm -rf /var/lib/apt/lists/*

ENV GRADLE_VERSION=gradle-8.4
RUN wget --no-verbose https://services.gradle.org/distributions/${GRADLE_VERSION}-bin.zip -O /tmp/${GRADLE_VERSION}-bin.zip \
    && unzip -q -d /opt/gradle /tmp/${GRADLE_VERSION}-bin.zip \
    && rm /tmp/${GRADLE_VERSION}-bin.zip

RUN npm install --global yarn \
    && yarn global add @tact-lang/compiler

RUN apt install python3

COPY tsa-cli/build/libs/tsa-cli.jar /home/tsa.jar
COPY resources/tlbc /home/tlbc
COPY resources/libcrypto.so.3 /lib/x86_64-linux-gnu/
COPY resources/entrypoint.py /home/entrypoint.py
COPY resources/block.tlb /home/block.tlb

ENV PATH="/opt/gradle/${GRADLE_VERSION}/bin:${PATH}"

ENTRYPOINT ["python3", "/home/entrypoint.py"]
