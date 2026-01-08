# embedded-wallet

This is a library which wraps [`libwally-core`](https://github.com/ElementsProject/libwally-core) and
* is built with meson, instead of `libwally`'s `autogen` + `make` build system
* provides configuration that's friendly embedded systems
* exposes a misuse resistant API

We intend to use this in W3 initially and Subzero 2 eventually.

## usage

```c
// NOTE: Error checks omitted for brevity; check all return values for EW_OK!

// Call ew_init() with your platform callbacks; see ew.h for the list of callbacks.
ew_init(&platform_api);

// Generate a seed; persist it somewhere securely.
uint8_t seed[EW_SEED_SIZE] = {0};
ew_seed_generate(seed);

// Sign PSBTs.

// Get the PSBT over I/O (nfc, qr code, etc.)
uint32_t psbt_bytes_len = read_psbt_len_from_io();
uint8_t *psbt_bytes = read_psbt_from_io();

// Determine overhead for signatures.
uint32_t psbt_signed_max;
ew_psbt_get_max_signed_size(psbt_bytes, psbt_bytes_len, &psbt_out_max);

uint8_t *psbt_signed = malloc(psbt_out_max);  // Or whatever allocator you use.
uint32_t psbt_signed_len = 0;

ew_psbt_sign(psbt_bytes,
             psbt_bytes_len,
             psbt_signed,
             psbt_signed_max,
             &psbt_signed_len,
             seed,
             true /* mainnet */);
```

## demo

Run `just demo`.
