# syntax=docker/dockerfile:1.7
FROM alpine:3 AS builder

ARG TERRAGRUNT_VERSION
ARG TERRAFORM_VERSION
ARG TERRAGRUNT_ATLANTIS_CONFIG_VERSION

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
curl -sSL https://github.com/transcend-io/terragrunt-atlantis-config/releases/download/v${TERRAGRUNT_ATLANTIS_CONFIG_VERSION}/terragrunt-atlantis-config_${TERRAGRUNT_ATLANTIS_CONFIG_VERSION}_linux_amd64.tar.gz | \
  tar xz --strip-components=1
mv terragrunt-atlantis-config_${TERRAGRUNT_ATLANTIS_CONFIG_VERSION}_linux_amd64 out/terragrunt-atlantis-config
chmod +x out/terragrunt-atlantis-config
EOF

# version: 0.28.3
FROM ghcr.io/runatlantis/atlantis@sha256:3361201124e497fe7a17061326ec998c73065a56b2ebc28222bd8d57f667d36d
COPY ./policies /policies
COPY --from=builder /binaries/out/* /usr/local/bin

# terragrunt prefers tofu to terraform if both are installed, and atlantis installs both; delete tofu
USER root
RUN rm -f /usr/local/bin/tofu
USER atlantis
