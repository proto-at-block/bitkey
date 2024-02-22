#!/bin/bash

# rust toolchain support for musl x86 linux, which is what you need to run on x86 lambda.
rustup target add x86_64-unknown-linux-musl
# install buildchain
brew tap messense/macos-cross-toolchains
brew install x86_64-unknown-linux-musl
# tell cargo where to find the right linker and gcc
export CARGO_TARGET_X86_64_UNKNOWN_LINUX_MUSL_LINKER=x86_64-unknown-linux-musl-gcc
export CC_x86_64_unknown_linux_musl=x86_64-unknown-linux-musl-gcc
# build!
cargo build --release --target=x86_64-unknown-linux-musl
