FROM ghcr.io/cirruslabs/android-sdk:35

ENV DEBIAN_FRONTEND=noninteractive \
    BUN_INSTALL=/opt/bun \
    ANDROID_HOME=/opt/android-sdk \
    GRADLE_USER_HOME=/home/vscode/.gradle

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl git unzip \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL https://bun.sh/install | bash \
    && ln -sf /opt/bun/bin/bun /usr/local/bin/bun \
    && ln -sf /opt/bun/bin/bunx /usr/local/bin/bunx

ENV PATH=/opt/bun/bin:${PATH}:/opt/android-sdk/platform-tools:/opt/android-sdk/cmdline-tools/latest/bin

RUN java -version \
    && bun --version \
    && adb version
