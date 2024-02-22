#!/usr/bin/env sh

set -euox pipefail

# script for manual testing of delta updates.
# assumes a dev unit, with firmware in slot a.

TMPDIR=/tmp/delta-build-test
TMPBUNDLE=/tmp/delta-bundle-test/

from_version=$(inv version)

inv clean build.platforms flash

rm -rf $TMPDIR
cp -r build $TMPDIR

inv bump clean build.platforms

to_version=$(inv version)

inv fwup.bundle-delta -p w1a -i dev -h dvt \
  --from-dir $TMPDIR/firmware/app/w1/application \
  --to-dir build/firmware/app/w1/application \
  --from-version $from_version --to-version $to_version --bundle-dir $TMPBUNDLE

inv fwup.fwup --binary $TMPBUNDLE/fwup-bundle-delta-$from_version-to-$to_version/w1a-dvt-a-to-b.signed.patch \
 --signature $TMPBUNDLE/fwup-bundle-delta-$from_version-to-$to_version/w1a-dvt-app-b-dev.detached_signature \
 --mode FWUP_MODE_DELTA_ONESHOT
