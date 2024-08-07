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
cp build-variables-customer-$release_version.json build-variables-customer.json

echo "--- Building customer AAB"
ANDROID_FLAVOR=customer app/verifiable-build/android/release/build-android-release-aab . ../verification-customer build-variables-customer.json > /dev/null
cd ../verification-customer/outputs/bundle/customer
mv app-customer.aab app-customer-$release_version.aab
sha256sum "app-customer-$release_version.aab" > "app-customer-$release_version-buildkite.aab.sha256"

echo "--- Uploading artifacts"
buildkite-agent artifact upload app-customer-$release_version-buildkite.aab.sha256
