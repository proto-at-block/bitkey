set -euo pipefail

release_version=$(buildkite-agent meta-data get "release-version")
release_tag="app/customer/$release_version"

echo "--- Checking out release tag"
git checkout $release_tag

echo "--- Downloading release artifacts for $release_tag to retrieve build variables"
gh release download $release_tag

echo "--- Checking downloaded artifacts checksums"
sha256sum -c ./*.sha256

echo "--- Copying build variables"
cp build-variables-emergency-$release_version.json build-variables-emergency.json

echo "--- Building emergency apk"
ANDROID_FLAVOR=emergency app/verifiable-build/android/release/build-android-release-apk . ../verification-emergency build-variables-emergency.json > /dev/null
cd ../verification-emergency/outputs/apk/emergency
mv app-emergency-unsigned.apk app-emergency-$release_version-unsigned.apk
sha256sum "app-emergency-$release_version-unsigned.apk" > "app-emergency-$release_version-unsigned-buildkite.apk.sha256"

echo "--- Uploading artifacts"
buildkite-agent artifact upload app-emergency-$release_version-unsigned-buildkite.apk.sha256