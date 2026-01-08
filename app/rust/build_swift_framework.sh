#!/bin/bash

set -euo pipefail

PACKAGE=$1
LIB=$2
FRAMEWORK=$3
if [ -z "$PACKAGE" ]; then
  echo "$0: missing package name" >&2
  exit 1
fi
if [ -z "$LIB" ]; then
  echo "$0: missing library name" >&2
  exit 1
fi
if [ -z "$FRAMEWORK" ]; then
  echo "$0: missing framework name" >&2
  exit 1
fi

find_root() {
	RUST_ROOT=$(git rev-parse --show-toplevel)/app/rust

	if [[ ! -d $RUST_ROOT ]]; then
		echo "$0: unable to find app/rust directory" >&2
		exit 1
	fi
}

find_sim_triple() {
	case $(uname -m) in
	    x86_64)
	    	echo -n x86_64-apple-ios
		;;
	    aarch64 | arm64)
	    	echo -n aarch64-apple-ios-sim
		;;
	    *)
		echo "Unsupported architecture: $(uname -m)" >&2
		exit 1
		;;
	esac
}

build() {
	local target=$1

	# rustup only detects rust-toolchain.toml in the cwd
	(
		cd "$RUST_ROOT"
		rustup target add $target
		
		# Set platform-specific environment variables
		if [[ $target == *apple-ios* ]]; then
			IOS_VERSION="15.2"
			export IPHONEOS_DEPLOYMENT_TARGET=$IOS_VERSION
			
			# Clear any existing flags
			unset CFLAGS CXXFLAGS RUSTFLAGS ASMFLAGS
			
			# Determine build type and set appropriate flags
			if [[ $target == *simulator* ]] || [[ $target == *sim ]]; then
				SDK_TYPE="iphonesimulator"
				VERSION_FLAG="-mios-simulator-version-min=$IOS_VERSION"
			else
				SDK_TYPE="iphoneos"
				VERSION_FLAG="-miphoneos-version-min=$IOS_VERSION"
			fi
			
			export CFLAGS="$VERSION_FLAG"
			export CXXFLAGS="$VERSION_FLAG"
			export RUSTFLAGS="-C link-arg=$VERSION_FLAG"
			
			# Ensure we're using the right SDK
			export SDKROOT="$(xcrun --sdk $SDK_TYPE --show-sdk-path)"
		fi
		
		cargo build \
			--lib \
			--package=$PACKAGE \
			--release \
			--target=$target \
			--locked
	)
}

lib() {
	local framework_root=$1
	shift
	local targets=$*

	mkdir -p $framework_root
	lipo \
		-create $(printf "$BUILD_TARGET/%s/release/lib$LIB.a\n" $targets) \
		-output $framework_root/$FRAMEWORK
}

bindgen() {
	local target=$1
	local swift_root=$2

	(
		cd "$RUST_ROOT"
		cargo run \
			--bin uniffi-bindgen \
			generate \
			--library $BUILD_TARGET/$target/release/lib$LIB.dylib \
			--language swift \
			--no-format \
			--out-dir $swift_root
	)
}

header() {
	local framework_headers=$1/Headers
	mkdir -p $framework_headers

	local swift_root=$2
	cp $swift_root/$FRAMEWORK.h $framework_headers
}

modulemap() {
	local framework_modules=$1/Modules
	mkdir -p $framework_modules

	local swift_root=$2
	sed \
		-e "s/^module/framework module/" \
		$swift_root/$FRAMEWORK.modulemap > $framework_modules/module.modulemap
}

infoplist() {
  local framework_root=$1
  local plist="$framework_root/Info.plist"

  if [ ! -f "$plist" ]; then
    /usr/libexec/PlistBuddy -c "Add :CFBundleDevelopmentRegion string en" "$plist"
    /usr/libexec/PlistBuddy -c "Add :CFBundleExecutable string $FRAMEWORK" "$plist"
    /usr/libexec/PlistBuddy -c "Add :CFBundleIdentifier string build.wallet.rust.$LIB" "$plist"
    /usr/libexec/PlistBuddy -c "Add :CFBundleInfoDictionaryVersion string 6.0" "$plist"
    /usr/libexec/PlistBuddy -c "Add :CFBundlePackageType string FMWK" "$plist"
    # The following values are required. Without them, the App Store will return an "Asset validation failed" error.
    /usr/libexec/PlistBuddy -c "Add :CFBundleShortVersionString string 1.0" "$plist"
    /usr/libexec/PlistBuddy -c "Add :CFBundleVersion string 1" "$plist"
    /usr/libexec/PlistBuddy -c "Add :MinimumOSVersion string 15.2" "$plist"
  fi
}

framework() {
	local framework_root=$RUST_BUILD_DIRECTORY/ios/$1/$FRAMEWORK.framework
	shift
	local targets=$*

	for target in $targets; do
		build $target
	done

	lib $framework_root $targets
	local swift_root=$RUST_BUILD_DIRECTORY/uniffi/swift
	bindgen $target $swift_root
	header $framework_root $swift_root
	modulemap $framework_root $swift_root
	infoplist $framework_root
}

xcframework() {
	local output=$RUST_BUILD_DIRECTORY/ios/$FRAMEWORK.xcframework

	[ -d "$output" ] && rm -rf "$output"
	xcodebuild \
		-create-xcframework \
		$(printf -- "-framework $RUST_BUILD_DIRECTORY/ios/%s/$FRAMEWORK.framework\n" $*) \
		-output $output
}

find_root
RUST_BUILD_DIRECTORY=$RUST_ROOT/_build/rust
BUILD_TARGET=$RUST_BUILD_DIRECTORY/target

if [ -n "${CI:-}" ]; then
  if [ "${IOS_SIMULATOR_ONLY:-false}" = "true" ]; then
    echo "Building iOS simulator target only"
    framework ios-sim $(find_sim_triple)
    xcframework ios-sim
  else
    echo "Building iOS device target only"
    framework ios aarch64-apple-ios
    xcframework ios
  fi
else
  echo "Building iOS device and simulator targets"
  framework ios aarch64-apple-ios
  framework ios-sim $(find_sim_triple)
  xcframework ios ios-sim
fi
