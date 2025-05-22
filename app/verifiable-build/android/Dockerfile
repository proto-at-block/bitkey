FROM ubuntu:jammy-20240911.1 AS base

ENV ANDROID_BUILD_TOOLS_VERSION="35.0.0"
ENV ANDROID_NDK_VERSION="25.2.9519653"

RUN apt update

RUN apt install -y --no-install-recommends --no-install-suggests \
  git=1:2.34.1-1ubuntu1.12 \
  build-essential=12.9ubuntu3 \
  libclang1=1:14.0-55~exp2 \
  openjdk-17-jdk=17.0.15+6~us1-0ubuntu1~22.04 \
  curl=7.81.0-1ubuntu1.20 \
  sdkmanager=0.5.1-1 \
  unzip=6.0-26ubuntu3 \
  zip=3.0-12build2

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_NDK_ROOT=$ANDROID_HOME/ndk/$ANDROID_NDK_VERSION
ENV PATH=$ANDROID_HOME/tools:$PATH
ENV PATH=$ANDROID_HOME/platform-tools:$PATH

ENV REPRODUCIBLE_BUILD_TEST=$REPRODUCIBLE_BUILD_TEST

RUN sdkmanager "platform-tools" "build-tools;$ANDROID_BUILD_TOOLS_VERSION"
RUN sdkmanager --install "ndk;$ANDROID_NDK_VERSION"

# Disables build scan plugin
ENV CI=true

COPY . /build

WORKDIR /build

RUN ./app/verifiable-build/android/common/accept-sdk-license

# Disable unnecessary download of Kotlin native compiler
RUN printf "\nkotlin.native.toolchain.enabled=false\n" >> app/gradle.properties

ENTRYPOINT ["/bin/bash", "-c"]

FROM base AS build

ARG REPRODUCIBLE_BUILD_VARIABLES

RUN echo "$REPRODUCIBLE_BUILD_VARIABLES" > /build/reproducible-build-variables.json

ENV REPRODUCIBLE_BUILD=/build/reproducible-build-variables.json
