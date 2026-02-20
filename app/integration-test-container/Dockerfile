# Dockerfile for running JVM integration tests in a container
# This allows tests to run within the same Docker network as backend services

FROM ubuntu:jammy-20240911.1

# Cache-busting arg: changes when app/ or server/ source changes
# This ensures Docker rebuilds when source code is modified
ARG SOURCE_HASH=unknown

# Install system dependencies
# Note: clang/libclang-dev is needed for Rust bindgen and firmware C code compilation
# protobuf-compiler is needed for Rust builds that use prost
# cmake is needed for some Rust crates that build native code
RUN apt-get update && apt-get install -y --no-install-recommends --no-install-suggests \
  git \
  build-essential \
  clang \
  cmake \
  libclang-dev \
  llvm-dev \
  pkg-config \
  libssl-dev \
  protobuf-compiler \
  openjdk-17-jdk \
  curl \
  unzip \
  && rm -rf /var/lib/apt/lists/*

# Install Rust
ENV RUSTUP_HOME=/usr/local/rustup
ENV CARGO_HOME=/usr/local/cargo
ENV PATH=/usr/local/cargo/bin:$PATH
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain 1.86.0 --profile default

# Install gobley-uniffi-bindgen for generating UniFFI Kotlin bindings
RUN cargo install gobley-uniffi-bindgen@0.3.7

# Disable sccache - not available in container and not needed (we use volume caching)
ENV RUSTC_WRAPPER=""

# Install Gradle (must match version in gradle/wrapper/gradle-wrapper.properties)
ENV GRADLE_VERSION=9.2.1
ENV GRADLE_HOME=/opt/gradle
RUN curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
  && unzip /tmp/gradle.zip -d /opt \
  && mv "/opt/gradle-${GRADLE_VERSION}" "${GRADLE_HOME}" \
  && rm /tmp/gradle.zip
ENV PATH=${GRADLE_HOME}/bin:$PATH

# Install just (command runner)
RUN curl --proto '=https' --tlsv1.2 -sSf https://just.systems/install.sh | bash -s -- --to /usr/local/bin

# Disable Gradle daemon and build scans for CI
ENV CI=true
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"

# Set working directory
WORKDIR /workspace

# Environment variables for connecting to backend services in Docker network
# These override the defaults in AppTester.kt which point to localhost
ENV F8E_ENVIRONMENT=http://toxiproxy:8080
ENV ELECTRUM_HTTP_URL=http://esplora:8100
ENV BITCOIND_DOMAIN=bitcoind:18443
ENV BITCOIND_USER=test
ENV BITCOIND_PASSWORD=test
ENV BITCOIN_NETWORK=REGTEST

# Note: Gradle dependencies are cached in the gradle-cache volume between runs.
# The first run will be slow as it downloads dependencies, but subsequent runs
# will be fast. Baking dependencies into the image requires copying the entire
# project structure which is complex and defeats the purpose of volume mounts.

# Default command runs integration tests
# Uses clean to avoid path conflicts from host builds
# Uses --rerun to force test execution and catch flaky tests
CMD ["gradle", "--no-daemon", "--no-watch-fs", "clean", "jvmIntegrationTest", "--rerun"]
