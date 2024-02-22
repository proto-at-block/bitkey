[![crates.io](https://img.shields.io/crates/v/urn.svg)](https://crates.io/crates/urn)
[![docs.rs](https://docs.rs/urn/badge.svg)](https://docs.rs/urn)

# URN

A Rust crate for handling
[URNs](https://datatracker.ietf.org/doc/html/rfc8141). Parsing
and comparison is done according to the spec (meaning only part of the
URN is used for equality checks). Some RFCs define per-namespace lexical
equivalence rules, those aren't taken into account here.

RFC2141 is more lenient than RFC8141 at times (and vice versa), care is
taken to be able to parse either of them. When percent encoding URN
components, the resulting URNs will always be valid for both RFC2141 and
RFC8141 parsers. However, percent encoding/decoding rules may be
different for some namespaces.

Serde support is available behind a feature flag. `no_std` support is
available if you disable the default "std" feature. `alloc` is optional
as well. `UrnSlice` is a borrowed URN, `Urn` is an owned URN. See
[docs.rs](https://docs.rs/urn) for documentation.

URNs have a surprising amount of obscure details to the point I'm not
sure if other URN parsers can be trusted! Granted, there's very little
of them because almost nobody really needs URNs...

## Roadmap

Currently, I'm looking for options to integrate with the
`percent-encoding` crate. If that isn't possible, I still want to make
percent-encoding/decoding functions return an iterator rather than a
`String`. Once that's done, I think the crate will be ready for 1.0.

Additionally, I may add functions for getting certain subslices of the
URN. For example, the RFC recommends not to pass the q-component and the
f-component to resolution services, so I can add a function that returns
the part of the URN that doesn't have the q-component and f-component.
The only open question for this API is the naming.

## Changelog

- 0.1.0 - initial release
- 0.1.1 - add `FromStr` impl
- 0.2.0 - remove `Urn::parse` function in favor of `FromStr`, improved
  docs
- 0.2.1 - remove files left over from 0.1
- 0.3.0 - major implementation changes, remove `Namespace` (thanks to
  u/chris-morgan for help)
- 0.3.1 - fix a panic on empty NSS and add "?=" terminator to
  r-component (both "?" and "=" can be part of r-component, but together
  they terminate it)
- 0.3.2 - add `Clone` impl for `Urn`
- 0.3.3 - more precise builder errors; reduce memory footprint by up to
  15 bytes (but increase it by 5 bytes on 16-bit platforms)
- 0.3.4 - Serde support by @callym
- 0.4.0 - `UrnBuilder::namespace` -> `UrnBuilder::nid`
- 0.5.0 - changed builder API to accept options for optional components,
  minor cleanup, fixed a couple potential minor bugs
- 0.5.1 - fix a panic in case there wasn't a valid utf-8 char boundary 4
  bytes into the string
- 0.6.0 - add `alloc` feature, add `UrnSlice` type, add `percent`
  module, don't impl `Deref<Target = str>`. The crate is getting close
  to 1.0.
- 0.7.0 - add support for deserializing non-`'static` `UrnSlice`s,
  always encode valid RFC2141 URNs, check for empty string in
  `percent::encode_*`.

## License

TL;DR do whatever you want.

Licensed under either the [BSD Zero Clause License](LICENSE-0BSD)
(https://opensource.org/licenses/0BSD), the [Apache 2.0
License](LICENSE-APACHE) (http://www.apache.org/licenses/LICENSE-2.0) or
the [MIT License](LICENSE-MIT) (http://opensource.org/licenses/MIT), at
your choice.

