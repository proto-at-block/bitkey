# syntax=docker/dockerfile:1.12
FROM rust:alpine AS build
RUN <<EOF
set -e
apk update
apk add --no-cache openssl-dev musl-dev make automake
rustup update
rustup default stable
EOF

COPY --link auth_lambda_handlers /root/auth_lambda_handlers

# Build the project after copying files over
WORKDIR /root/auth_lambda_handlers
RUN \
  --mount=type=cache,id=target,target=/root/auth_lambda_handlers/target \
  --mount=type=cache,id=cargo,target=/usr/local/cargo/registry \
  <<EOF
  set -e
  make buildlambda
  ls -al target/release
  mkdir /root/binaries
  MACHINE=$(uname -m)
  cp \
    target/${MACHINE}-unknown-linux-musl/release/create_auth_challenge \
    target/${MACHINE}-unknown-linux-musl/release/define_auth_challenge \
    target/${MACHINE}-unknown-linux-musl/release/verify_auth_challenge \
    target/${MACHINE}-unknown-linux-musl/release/pre_token_generation \
    /root/binaries/
EOF
