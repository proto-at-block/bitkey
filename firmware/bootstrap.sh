#!/bin/bash

set -euo pipefail

# NOTE: If you make changes, please lint with shellcheck.
# TODO: shellcheck on CI.

# Activate hermit
source ./bin/activate-hermit || true

# Install casks
pkg-config --exists criterion || brew install criterion

# Install requirements
pip3 install --quiet --requirement requirements.txt
