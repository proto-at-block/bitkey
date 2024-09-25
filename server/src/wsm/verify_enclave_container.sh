#!/bin/bash

set -euo pipefail

export DOCKER_PLATFORM="${DOCKER_PLATFORM:-linux/arm64}"
export GIT_BRANCH="${GIT_BRANCH:-main}"

# This script will build the wsm-enclave container locally. Then it will unpack all the layers of that container
# and a wsm-enclave container that has been build by the CI system. It then checks that all the layers in each container
# have identical contents.

# wrap pushd and popd so we don't get terminal output everytime we change directories
pushd () {
    command pushd "$@" > /dev/null || exit
}

popd () {
    command popd > /dev/null || exit
}

usage () {
  echo "Verify the wsm-enclave container built on CI by building it locally and verifying that the contents match"
  echo ""
  echo "Usage: $0 <commit>"
  echo "  Download the containers built for this <commit> from GitHub Actions and verify locally"
  echo ""
  echo "Usage: $0 <container_tar> <eif>"
  echo "  Verify locally against pre-downloaded artifacts"
}

check_head() {
  local head=$(git rev-parse HEAD)

  if [[ "$COMMIT" != "$head" ]]; then
    cat <<EOF

    ⚠️ WARNING: HEAD is on "$head" but checking against build for "$COMMIT".

EOF
  fi
}

download_artifacts() {
  set -e
  local run_id=$(gh run list -w server -b "${GIT_BRANCH}" --limit 100 --json 'conclusion,databaseId,headSha' | jq --arg COMMIT "$COMMIT" -r 'map(select(.conclusion == "success" and .headSha == $COMMIT))[0].databaseId')

  if [[ "$run_id" == "null" ]]; then
    echo "Successful GitHub Actions run not found for $COMMIT"
    exit 1
  fi

  echo "Found GitHub Run https://github.com/squareup/wallet/actions/runs/$run_id"

  mkdir -p build
  pushd build
  rm -rf wsm-*

  echo "⬇️  downloading WSM Enclave"
  gh run download $run_id -n wsm-enclave-container.tar
  gh run download $run_id -n wsm-enclave.eif
  echo "✅ Downloaded
    $PWD/wsm-enclave-container.tar
    $PWD/wsm-enclave.eif
  "
  CONTAINER_TAR=$PWD/wsm-enclave-container.tar
  EIF=$PWD/wsm-enclave.eif

  popd
}

if [[ $# -lt 1 ]]; then
  usage
  exit 1
fi

if [[ $# -eq 1 ]]; then
  if ! [[ $1 =~ ^[0-9a-f]{40}$ ]]; then
    echo "$1 is not a valid Git commit SHA"
    echo ""
    usage
  fi
  COMMIT="$1"
  check_head
  download_artifacts
else
  CONTAINER_TAR=$1
  EIF=$2
fi

echo "**************************************"
echo "starting ${DOCKER_PLATFORM} build of the container"
echo "**************************************"
pushd ../..
docker buildx bake \
   --set '*.platform'=${DOCKER_PLATFORM} \
   --set wsm-enclave.args.BUILD_ON_MAC=true \
   --set wsm-enclave.tags=wsm-enclave:local \
   wsm-enclave nitro-cli
popd

# unpack container filesystem contents into two directories so we can compare them
tempdir=$(mktemp -d -t wsm-image-verification)
echo "Using $tempdir for unpacking containers and comparing their contents"
mkdir -p $tempdir/{ci,local,eif}
cp $CONTAINER_TAR $tempdir/ci/container.tar
cp $EIF $tempdir/eif/wsm-enclave.eif
pushd $tempdir
cd ci
echo "Unpacking CI-built container image"
docker load -i container.tar
tar xf container.tar
cd $tempdir/local
echo "Unpacking locally-build container image"
docker save wsm-enclave:local -o container.tar
tar xf container.tar
cd $tempdir
# make sure images have same length
CI_LAYER_COUNT=$(docker inspect wsm-enclave:latest  | jq '.[0].RootFS.Layers | length')
LOCAL_LAYER_COUNT=$(docker inspect wsm-enclave:local  | jq '.[0].RootFS.Layers | length')
if [[ ! $CI_LAYER_COUNT -eq $LOCAL_LAYER_COUNT ]]; then
  echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
  echo "⛔ IMAGES HAVE DIFFERENT NUMBER OF LAYERS! Something is wrong."
  echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
  exit 1
fi

echo "✅ Images have the same number of layers. Moving on to check layer contents..."

function extract_rootfs() {
  image="$1"
  out="$2"
  container_id=$(docker create --platform=${DOCKER_PLATFORM} "$1")
  docker export "$container_id" -o "rootfs.tar.gz"
  docker rm -f "$container_id" >/dev/null
  tar xf rootfs.tar.gz -C "$out"
  rm rootfs.tar.gz
}
mkdir {ci,local}/rootfs
extract_rootfs wsm-enclave:latest ci/rootfs
extract_rootfs wsm-enclave:local local/rootfs

cd $tempdir/ci/rootfs/
# recursively sha256 everything in the directory
find . -type f -exec shasum -a 256 {} \; | sort > ../../ci-hashes.txt

cd $tempdir/local/rootfs/
# recursively sha256 everything in the directory
find . -type f -exec shasum -a 256 {} \; | sort > ../../local-hashes.txt

cd $tempdir
# Hash the hashes together
CI_FS_COMPUTED_HASH=$(cat ci-hashes.txt | shasum -a 256 | cut -d ' ' -f 1)
LOCAL_FS_COMPUTED_HASH=$(cat local-hashes.txt | shasum -a 256 | cut -d ' ' -f 1)
if [[ ! "$CI_FS_COMPUTED_HASH" = "$LOCAL_FS_COMPUTED_HASH" ]]; then
  diff --unified=0 ci-hashes.txt local-hashes.txt || true
  echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
  echo "⛔ COMPUTED HASHES FOR THE CI-BUILT AND LOCALLY-BUILT CONTAINERS' ROOT FILESYSTEMS DO NOT MATCH"
  echo "⛔ SOMETHING IS WRONG."
  echo ""
  echo "ℹ️ Try clearing the Docker cache with"
  echo "       docker buildx prune --all"
  echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
  echo "You can look in $tempdir to inspect filesystem contents"
  exit 1
fi
echo "✅ Root filesystem of each container has contents with the same hash: $CI_FS_COMPUTED_HASH"
echo
echo "********************************"
echo "🎉 Container image verified! 🎉"
echo "********************************"
echo
echo "************************************************************"
echo "Checking EIF image to make sure it uses that container image"
echo "************************************************************"
EIF_INFO=$(
  docker run --rm -it -v $PWD/eif:/eif --platform ${DOCKER_PLATFORM} nitro-cli:latest \
    nitro-cli describe-eif --eif-path /eif/wsm-enclave.eif
)
EIF_IMAGE_HASH=$(echo $EIF_INFO | jq -r .ImageVersion)
CONTAINER_IMAGE_HASH=$(docker inspect wsm-enclave:latest | jq -r '.[0].Id' | cut -d : -f 2)
if [[ ! "$EIF_IMAGE_HASH" = "$CONTAINER_IMAGE_HASH" ]]; then
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    echo "⛔ ENCLAVE IMAGE (.eif file) IS USING DIFFERENT CONTAINER IMAGE"
    echo "⛔ SOMETHING IS WRONG"
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    exit 1
fi
echo
echo "*******************************"
echo "🎉 Enclave image verified! 🎉"
echo "*******************************"
echo
echo "When we go and build our attestation, you would want to go ahead and sign these PCRs:"
echo $EIF_INFO | jq -r .Measurements

popd
