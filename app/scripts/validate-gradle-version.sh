#!/bin/bash
#
# This script checks that versions in the Gradle wrapper configuration files match the installed
# Gradle version.

# We explicitly specify Gradle version in the Gradle wrapper configuration files, but we also
# install Gradle as part of the Hermit distribution.
#
# This is a workaround for the issue where IDE uses its own Gradle distribution for syncing, which
# prevents us from reusing Gradle daemon between IDE syncing and IDE/CLI builds:
# - https://youtrack.jetbrains.com/issue/IDEA-301544
# - https://issuetracker.google.com/issues/301064283
# For the workaround to work, we need to make sure that gradle-wrapper.properties files exist
# in every Gradle project (contains `settings.gradle.kts`), we have two today: `app` and `build-logic`.
#
# Run this script using `just validate-gradle-version`.

function fatal() {
  echo "$@" >&2
  exit 1
}

# Get the version from the installed Gradle
gradle_version=gradle-$(hermit info gradle --json | gojq -r '.[].Reference.Version')-all.zip
if [ -z "$gradle_version" ]; then
  fatal "Error: Could not determine Gradle version."
fi

# List of gradle-wrapper.properties files to check
files_to_check=("./gradle/wrapper/gradle-wrapper.properties" "./gradle/build-logic/gradle/wrapper/gradle-wrapper.properties")

# Loop through each file and compare the versions
for file in "${files_to_check[@]}"; do
  if [ -f "$file" ]; then
    wrapper_version=$(grep -Eo 'gradle-[0-9.]+-all.zip' "$file")
    if [ -z "$wrapper_version" ]; then
      fatal "Error: Could not determine wrapper version from $file."
    fi

    if [ "$gradle_version" != "$wrapper_version" ]; then
      fatal "Mismatch: Gradle version is '$gradle_version', but $file has version '$wrapper_version'"
    fi
  fi
done

