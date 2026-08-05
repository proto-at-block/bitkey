# syntax=docker/dockerfile:1.13
FROM alpine:3 AS builder

ARG TERRAGRUNT_VERSION
ARG TERRAFORM_VERSION
ARG CONFTEST_VERSION

RUN apk add curl unzip && mkdir -p /binaries/out/
WORKDIR /binaries/
RUN <<EOF
set -ex
curl -sSL "https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_linux_amd64.zip" -o terraform.zip
unzip terraform.zip
mv terraform out/terraform
chmod +x out/terraform
EOF

RUN <<EOF
set -ex
curl -sSL "https://github.com/gruntwork-io/terragrunt/releases/download/v${TERRAGRUNT_VERSION}/terragrunt_linux_amd64" -o out/terragrunt
chmod +x out/terragrunt
EOF

RUN <<EOF
set -ex
curl -sSL "https://github.com/open-policy-agent/conftest/releases/download/v${CONFTEST_VERSION}/conftest_${CONFTEST_VERSION}_Linux_x86_64.tar.gz" | \
  tar xz conftest
mv conftest out/conftest
chmod +x out/conftest
EOF

FROM ghcr.io/runatlantis/atlantis:v0.46-alpine@sha256:e79363da6b55182d0cb13d7d9deb1cef51528f309a64254755c80df09cbfd698
COPY ./policies /policies
COPY --from=builder /binaries/out/* /usr/local/bin

# Our Terraform and conftest copies replace the base image's defaults; keep the advertised
# default versions in sync so Atlantis resolves the patched, baked-in binaries.
ARG TERRAFORM_VERSION
ARG CONFTEST_VERSION
ENV DEFAULT_TERRAFORM_VERSION=${TERRAFORM_VERSION}
ENV DEFAULT_CONFTEST_VERSION=${CONFTEST_VERSION}

# terragrunt prefers tofu to terraform if both are installed, and atlantis installs both; delete tofu.
# Also delete the base image's versioned terraform/tofu side binaries: they embed go-getter < 1.8.6
# (CVE-2026-4660). Atlantis resolves built-in steps using versioned binary names and otherwise
# downloads them at runtime, so point those names at our patched, baked-in copies.
USER root
RUN rm -f /usr/local/bin/tofu /usr/local/bin/tofu1* /usr/local/bin/terraform1* && \
    ln -s /usr/local/bin/terraform /usr/local/bin/terraform${TERRAFORM_VERSION} && \
    ln -s /usr/local/bin/conftest /usr/local/bin/conftest${CONFTEST_VERSION}
USER atlantis
