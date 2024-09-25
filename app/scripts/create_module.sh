#!/bin/bash

set -e

# Generates a new module, creating the necessary directories, template build.gradle.kts files,
# a README, and updating settings.gradle.kts

# Prompt for module directory
read -p "Enter the module directory (e.g., shared): " moduleDirectory

# Prompt for module name
read -p "Enter the module name: " moduleName

# Prompt for module types as text
echo "Enter module types (space-separated):"
echo "Available types: fake impl public testing"
read -a moduleTypes

# Validate module types
validTypes=("fake" "impl" "public" "testing")
for type in "${moduleTypes[@]}"; do
    if [[ ! " ${validTypes[@]} " =~ " ${type} " ]]; then
        echo "Invalid module type: $type"
        exit 1
    fi
done

# Prompt for source sets as text
echo "Enter source sets (space-separated):"
echo "Available source sets: commonMain commonJvmMain iosMain jvmMain androidMain commonTest commonJvmTest iosTest jvmTest androidUnitTest commonIntegrationTest commonJvmIntegrationTest jvmIntegrationTest"
read -a sourceSets

# Validate source sets
validSourceSets=("commonMain" "commonJvmMain" "iosMain" "jvmMain" "androidMain" "commonTest" "commonJvmTest" "iosTest" "jvmTest" "androidUnitTest" "commonIntegrationTest" "commonJvmIntegrationTest" "jvmIntegrationTest")
for srcSet in "${sourceSets[@]}"; do
    if [[ ! " ${validSourceSets[@]} " =~ " ${srcSet} " ]]; then
        echo "Invalid source set: $srcSet"
        exit 1
    fi
done

# Update settings.gradle.kts with the latest module
# Setting the path to the settings.gradle.kts file
settingsFile="app/settings.gradle.kts"

# Backup the settings.gradle.kts file
cp "$settingsFile" "$settingsFile.bak"

# Insert new module names in settings.gradle.kts
for moduleType in "${moduleTypes[@]}"; do
    moduleEntry="module(\":$moduleDirectory:$moduleName:$moduleType\")"

    # Check if the module entry already exists
    if ! grep -q "$moduleEntry" "$settingsFile"; then
        echo "$moduleEntry" >> "$settingsFile"
    fi
done

# Alphabetize module lines in settings.gradle.kts and append them to the bottom
grep '^module("' "$settingsFile" | sort > temp_sorted_modules.txt
grep -v '^module("' "$settingsFile" > temp_settings_without_modules.txt
cat temp_settings_without_modules.txt temp_sorted_modules.txt > "$settingsFile"

# Clean up temporary files
rm temp_sorted_modules.txt temp_settings_without_modules.txt
rm "$settingsFile.bak"

# Create the directory structure
for moduleType in "${moduleTypes[@]}"; do
    moduleDir="app/$moduleDirectory/$moduleName/$moduleType"

   for sourceSetName in "${sourceSets[@]}"; do
          srcDir="$moduleDir/src/$sourceSetName/kotlin/build/wallet/$moduleName"
          mkdir -p "$srcDir"

          # Create a dummy .kt file in each source set directory
          dummyKtFile="$srcDir/Dummy.kt"
          echo "package build.wallet.$moduleName" >> "$dummyKtFile"
          echo "" >> "$dummyKtFile"
          echo "// Dummy Kotlin file for $sourceSetName" > "$dummyKtFile"
          echo "class Dummy {" >> "$dummyKtFile"
          echo "    // TODO: Implement functionality" >> "$dummyKtFile"
          echo "}" >> "$dummyKtFile"
      done

    # Create a basic build.gradle.kts file
    cat <<EOL > "$moduleDir/build.gradle.kts"

import build.wallet.gradle.logic.extensions.allTargets

plugins {
  id("build.wallet.kmp")
}

kotlin {
  allTargets()

  sourceSets {
EOL

  for sourceSetName in "${sourceSets[@]}"; do
      echo "        val $sourceSetName by getting" >> "$moduleDir/build.gradle.kts"
  done

  cat <<EOL >> "$moduleDir/build.gradle.kts"
  }
}
EOL

done

# Create a README.md file in the module directory
readmeFile="app/$moduleDirectory/$moduleName/README.md"
echo "# $moduleName" > "$readmeFile"

echo "Modules created successfully."
echo "Make sure to update your build.gradle.kts and README.md files!"