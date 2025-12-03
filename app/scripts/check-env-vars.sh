#!/usr/bin/env bash

set -e

echo "Checking required environment variables..."

all_set=true

# Check ANDROID_HOME
if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
  echo "✅ ANDROID_HOME is set: $ANDROID_HOME"
else
  echo "❌ ANDROID_HOME is not set or directory doesn't exist"
  echo "   Add to ~/.zprofile: export ANDROID_HOME=\"\$HOME/Library/Android/sdk\""
  all_set=false
fi

# Check ANDROID_NDK_VERSION
expected_ndk="25.2.9519653"
if [ "$ANDROID_NDK_VERSION" = "$expected_ndk" ]; then
  echo "✅ ANDROID_NDK_VERSION is set: $ANDROID_NDK_VERSION"
else
  echo "❌ ANDROID_NDK_VERSION is not set correctly (expected: $expected_ndk)"
  echo "   Add to ~/.zprofile: export ANDROID_NDK_VERSION=\"$expected_ndk\""
  all_set=false
fi

# Check ANDROID_BUILD_TOOLS_VERSION
expected_build_tools="35.0.0"
if [ "$ANDROID_BUILD_TOOLS_VERSION" = "$expected_build_tools" ]; then
  echo "✅ ANDROID_BUILD_TOOLS_VERSION is set: $ANDROID_BUILD_TOOLS_VERSION"
else
  echo "❌ ANDROID_BUILD_TOOLS_VERSION is not set correctly (expected: $expected_build_tools)"
  echo "   Add to ~/.zprofile: export ANDROID_BUILD_TOOLS_VERSION=\"$expected_build_tools\""
  all_set=false
fi

# Check ANDROID_NDK_ROOT
if [ -n "$ANDROID_NDK_ROOT" ] && [ -d "$ANDROID_NDK_ROOT" ]; then
  echo "✅ ANDROID_NDK_ROOT is set: $ANDROID_NDK_ROOT"
else
  echo "❌ ANDROID_NDK_ROOT is not set or directory doesn't exist"
  echo "   Add to ~/.zprofile: export ANDROID_NDK_ROOT=\$ANDROID_HOME/ndk/\$ANDROID_NDK_VERSION"
  all_set=false
fi

# Check JAVA_HOME
if [ -n "$JAVA_HOME" ] && [ -d "$JAVA_HOME" ]; then
  echo "✅ JAVA_HOME is set: $JAVA_HOME"
else
  echo "❌ JAVA_HOME is not set or directory doesn't exist"
  echo "   Add to ~/.zprofile: export JAVA_HOME=\$(/usr/libexec/java_home -v 17)"
  all_set=false
fi

# Check if Android SDK tools are in PATH
if command -v adb &> /dev/null; then
  echo "✅ Android SDK tools are in PATH (adb found)"
else
  echo "❌ Android SDK tools not found in PATH"
  echo "   Add to ~/.zprofile:"
  echo "     export PATH=\$ANDROID_HOME/tools:\$PATH"
  echo "     export PATH=\$ANDROID_HOME/build-tools/\$ANDROID_BUILD_TOOLS_VERSION:\$PATH"
  echo "     export PATH=\$ANDROID_HOME/platform-tools:\$PATH"
  all_set=false
fi

echo ""
if [ "$all_set" = true ]; then
  exit 0
else
  echo "⚠️  Some environment variables need to be set. Add them to ~/.zprofile and restart your terminal."
  echo ""
  exit 1
fi

