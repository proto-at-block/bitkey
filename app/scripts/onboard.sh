#!/usr/bin/env bash

set -e

echo ""
echo "Starting Bitkey Mobile Onboarding..."
echo ""
echo "Checking prerequisites..."

# Check for Homebrew
if ! command -v brew &> /dev/null; then
  echo "❌ Homebrew is not installed."
  echo ""
  echo "Homebrew is required to install development tools."
  echo "Install it from: https://brew.sh"
  echo ""
  exit 1
fi
echo "✅ Homebrew is already installed"

# Check for Hermit activation
if ! command -v just &> /dev/null; then
  echo "❌ Hermit is not activated in this shell."
  echo ""
  echo "Hermit provides the 'just' command and other development tools."
  echo ""
  echo "Run this command first:"
  echo ". bin/activate-hermit"
  echo ""
  echo "Then run 'just onboard' again."
  echo ""
  exit 1
fi
echo "✅ Hermit is already activated"

echo ""
echo "Checking installed tools..."
just install-git-lfs
just install-xcode-clt
just install-jetbrains-toolbox
just install-xcode-kmp-plugin
just install-docker
just install-aws-creds
just install-coreutils
just install-swiftformat
echo ""
echo "Running setup tasks..."
just submodules 2>&1 | grep -v "^Updating git submodules"
just sync-aws-creds 2>&1 | grep -v "^Syncing AWS credentials" | grep -v "^Successfully synced"
echo ""
if ./scripts/check-env-vars.sh; then
  echo ""
  echo "✅ Onboarding complete! You're ready to build."
  echo ""
else
  echo ""
  echo "⚠️  Onboarding incomplete. Please set the missing environment variables."
  echo ""
  exit 1
fi

