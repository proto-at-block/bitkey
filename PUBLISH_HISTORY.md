# How This Repository Is Published

Bitkey is developed in a private repository inside Block. This repository is a
published copy of that source, made available so anyone can read and audit the
code that runs on Bitkey hardware, in the Bitkey apps, and on our servers.

Because it is a copy rather than the place development happens, the commit and
tag history here looks different from a normal project's. This document explains
what you are looking at.

## Commits are snapshots, not development history

Each commit here is a snapshot of the internal repository at a point in time, not
an individual code change. A single commit can touch thousands of files and
covers everything that changed internally since the previous publish. Commits are
authored by an automated publishing bot, and commit messages describe the publish
rather than the work.

Every publish commit carries a `Source-Commit:` trailer recording the internal
commit the snapshot was taken from. That identifier is meaningless outside Block,
but it lets our tooling keep publishes in order, and it lets you see that two
publishes came from different points in internal history.

## Two kinds of publishes

| | App release | Non-app publish |
|---|---|---|
| Commit subject | `Publish source <date>` | `Publish non-app source <timestamp>` |
| Tag | `app/<version>` | `source/<YYYY-MM-DD-HHMM>` (UTC) |
| `app/` tree | Updated | Unchanged |
| Everything else | Updated | Updated |
| GitHub Release | Yes | No |

**App releases** happen when a new version of the Bitkey mobile app ships to
customers. The snapshot covers the *entire* repository as of the commit the
released app was built from, and it is tagged `app/<version>`. Each one also gets
a [GitHub Release](https://github.com/proto-at-block/bitkey/releases) containing
the Android APK and the build metadata needed to reproduce it.

**Non-app publishes** happen between app releases. They refresh everything except
the `app/` directory — server, firmware, core libraries, and so on — so that work
on continuously deployed systems becomes public without waiting for the next
mobile release. They are tagged `source/<timestamp>` and have no accompanying
GitHub Release. They happen as needed rather than on a fixed schedule, so gaps
between them are normal.

## Why the `app/` directory only moves at app releases

The published app source is meant to correspond to an app you can actually
install. If we refreshed `app/` continuously, the code here would describe a
build that was never shipped to anyone. Instead, `app/` changes only at app
releases, and each release is tagged so it can be matched to the app it produced.

This matters if you are reproducing an Android build: check out the
`app/<version>` tag matching the APK you are verifying, not `main`. Between app
releases, `main` holds frozen app source alongside newer non-app source, so it
does not correspond to any released app. See
[app/verifiable-build/android/README.md](app/verifiable-build/android/README.md)
for the full verification process.

## Why non-app code sometimes appears to go backwards

An app release publishes the whole repository as of the commit its app was built
from. That commit is usually a little older than our most recent non-app publish,
because app builds are cut, tested, and staged for release over some days while
server and firmware work continues.

The result is that an app release can temporarily roll the non-app directories
back to an earlier state. When that happens, we run another non-app publish
shortly after the app release to bring them forward again.

So if you see a `Publish source` commit that appears to undo recent server or
firmware changes, followed by a `Publish non-app source` commit restoring them,
that is this mechanism — not a reversal of the underlying work. The `app/`
directory is never affected, since non-app publishes leave it untouched.

## Questions

For security reports, see the reporting program in the
[README](README.md). For background on why we publish, see
[Sharing the Code Behind Bitkey](https://bitkey.build/sharing-the-code-behind-bitkey/).
