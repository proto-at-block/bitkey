# syntax=docker/dockerfile:1.4
ARG ATLANTIS_VERSION

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
chmod +x terraform
mv terraform /usr/local/bin/terraform
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

FROM ghcr.io/runatlantis/atlantis:v${ATLANTIS_VERSION}
COPY ./policies /policies
COPY --from=builder /binaries/out/* /usr/local/bin
