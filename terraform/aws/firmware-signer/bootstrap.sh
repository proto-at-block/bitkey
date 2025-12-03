#!/bin/bash

set -euo pipefail

# Activate hermit environment
source ./bin/activate-hermit

# Install requirements
pip3 install --requirement requirements.txt
