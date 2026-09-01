# App Scripts

Owner: Mobile
Last reviewed: 2026-05-21

This directory contains executable helper scripts for mobile app development and CI.

## JVM Integration Test Shards

Shard-specific tooling and documentation live in `scripts/jvm-integration-test-shards/`.
## iOS Bugsnag Symbolication

Use `ios-symbolicate-bugsnag.py` when investigating Bitkey iOS Bugsnag crashes with unsymbolicated `Wallet <unknown>` stack frames. The script matches a Bugsnag binary UUID to a dSYM from a release archive, optionally verifies the IPA UUID, and runs `atos` for the provided frame addresses.

Typical workflow:

```bash
python3 app/scripts/ios-symbolicate-bugsnag.py \
  --uuid EBAB312A-8C73-35E1-AD16-223117084FC8 \
  --ipa ~/Downloads/app-customer-2026.11.0.ipa \
  --archive ~/Downloads/release_archive.xcarchive.tar.gz \
  --addresses 0x100bf13dc 0x10168904c 0x1016887b0 \
  --load-address <Bugsnag Wallet machoLoadAddress>
```

Find the UUID in Bugsnag under the event's App tab (`dSYM UUIDs` / `dsymUUIDs[0]`). IPA verification requires `--uuid` and exits non-zero without it. If available, also copy the `Wallet` `machoLoadAddress` / image load address and pass it with `--load-address`. For customer releases, the matching archive is usually the Buildkite `release_archive.xcarchive.tar.gz` artifact from the `runway/wallet` customer iOS release build.
