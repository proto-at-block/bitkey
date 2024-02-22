#!/usr/bin/env bash

set -euxo pipefail

sudo systemctl enable wsm-enclave.service
sudo systemctl restart wsm-enclave.service
sudo systemctl enable wsm-proxy.service
sudo systemctl restart wsm-proxy.service
sudo systemctl enable wsm-api.service
sudo systemctl restart wsm-api.service
