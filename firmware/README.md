# bitkey firmware

[![Firmware CI](https://github.com/squareup/wallet/actions/workflows/firmware.yml/badge.svg)](https://github.com/squareup/wallet/actions/workflows/firmware.yml)


## a note on building

Currently, external parties will not be able to build Bitkey firmware, because we use a 3rd party fingerprint sensor that comes with a proprietary matching algorithm and we are contractually obligated not to publicly release the library that implements this functionality. 

You can build the tests and fuzzers - as well as the firmware, if you implement stubs for the missing 3rd party library functionality.

## setup

Toolchain management is done with `Hermit`. To get started, run:

```bash
# ensure you have submodules
$ git submodule update --init --recursive

# bootstrap a new environment with pyenv
$ ./bootstrap.sh

# activate Hermit
$ source activate

# install JLinkGDBServer, only needed to flash and debug
$ inv install.jlink

# install SVD file (only if debugging with vscode)
$ inv install.svd
```

## using vscode

```bash
cd firmware
. ./activate
code ../wallet.code-workspace
```

## build

The build system is Meson, with an [`invoke`](https://www.pyinvoke.org/) wrapper for convenience.
Read more about invoking tasks [here](https://docs.pyinvoke.org/en/stable/concepts/invoking-tasks.html).

```bash
# View all available commands
$ inv --list
# Get help for a specific command
$ inv --help build
# Chain multiple commands
$ inv clean build flash
```

## nfc communication

W1 supports two application layer NFC interfaces:

1. NDEF
2. WCA (Wallet Custom APDUs)

### NDEF

NDEF is a standard protocol to interact with all NFC tags. NDEF is basically a simple API that lets you select, read, and write “NDEF files” (arbitrary data blocks) on a tag. We intend to use NDEF only for firmware update.

### WCA (Wallet Custom APDUs)

WCA is protobufs-over-APDUs, and will be used for all other app to firmware comms.

[The WCA command set is fully documented in Github.](https://github.com/squareup/wallet/tree/main/firmware/lib/wca) Refer there for the most up-to-date information. Since protos can be bigger than APDUs, WCA allows splitting up protos while sending or receiving.

