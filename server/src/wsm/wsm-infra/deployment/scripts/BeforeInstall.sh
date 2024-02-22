#!/usr/bin/env bash

set -euxo pipefail

sudo usermod -a -G systemd-journal dd-agent
sudo mkdir -p /etc/datadog-agent/conf.d/journald.d
sudo tee /etc/datadog-agent/conf.d/journald.d/conf.yaml > /dev/null <<EOF
logs:
  - type: journald
    service: wsm
    include_units:
      - nitro-enclaves-allocator.service
      - nitro-enclaves-vsock-proxy.service
      - wsm-api.service
      - wsm-enclave.service
      - wsm-proxy.service
EOF
sudo systemctl restart datadog-agent

sudo amazon-linux-extras install -y aws-nitro-enclaves-cli
sudo usermod -aG ne ec2-user
sudo systemctl enable nitro-enclaves-allocator.service
sudo systemctl start nitro-enclaves-allocator.service
sudo systemctl enable nitro-enclaves-vsock-proxy.service
sudo systemctl start nitro-enclaves-vsock-proxy.service

sudo mkdir -p /opt/wsm/
sudo chown -R ec2-user: /opt/wsm