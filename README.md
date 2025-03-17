# Bitkey

Bitkey is the safe, easy way to own and manage your bitcoin. Learn more at [bitkey.world](https://bitkey.world), or on our [development blog](https://bitkey.build)

For more information about our release of Bitkey's code, read [our blog post on the topic](https://bitkey.build/sharing-the-code-behind-bitkey/).

## Repository Structure

Each directory in our repository is a separate project— each with its own build and often in its own language(s) / platform(s).

| Directory                    | Description                                                           |
|------------------------------| --------------------------------------------------------------------- |
| [`app/android`](app/android) | Android mobile app                                                    |
| [`app/rust`](app/rust)       | Key and wallet management                                             |
| [`app/ios`](app/ios)         | iOS mobile app                                                        |
| [`app/shared`](app/shared)   | Kotlin Multiplatform code shared between Android and iOS              |
| [`app/style`](app/style)     | Style Dictionary (shared)                                             |
| [`app`](app)                 | Our mobile application's project root                                 |
| [`core`](core)               | Shared rust libraries                                                 |
| [`firmware`](firmware)       | Code that powers the Bitkey hardware device                           |
| [`nodes`](nodes)             | Nodes (bitcoind / electrum) and service                               |
| [`server`](server)           | API Server and Wallet Security Module (WSM)                           |
| [`terraform`](terraform)     | Our Infrastructure as Code                                            |

## Building

Bitkey source is available here for auditing purposes, but we're not ready to support external parties in building all Bitkey components. See individual project README information for more detail.

## Contributing
At this time, we are not accepting external contributions to Bitkey code. We're prioritizing building a smooth, safe self-custody experience through the Bitkey app and hardware through contributions from our internal team. For now, if you want to contribute to Bitkey, consider [joining our team](https://block.xyz/careers?search=bitkey).

## Security Reporting Program

To report potential security issues to us, please email us at bitkey-security@block.xyz.

To protect our customers, we ask that you report vulnerabilities to us as soon as you can, and that you coordinate disclosure with us first, so that we can understand and mitigate reported vulnerabilities before they are disclosed to anyone other than the Bitkey team or to the public. To accomplish this, we ask you to follow a 90-day disclosure policy. That is, we ask that you do not disclose the existence, discovery, and technical details of the vulnerability until the 90th day after you file a report with reasonable technical detail with the Bitkey team, and that you notify us prior to disclosure.

## Verifying Bitkey Mobile Applications

We want to enable anyone with sufficient technical skills,
to match the application built using the code in this repository
to the application distributed to customer on the app stores.

We currently have the Android verification process available.
Stay tuned for iOS verification, coming soon.

Head to [app/verifiable-build/android/README.md](app/verifiable-build/android/README.md)
to learn more about the verification process
and how to do do it yourself.

## Notes

* This document (including Software and schematics) is current as of 2024-02-22. Our goal is to make sure the code we publish is up to date with what is available in the App Store. We may publish other items on a less real time basis (e.g. server code and schematics) and so what is published may not always map to what is in production or design.

* Ownership of this document and all design elements of the Bitkey remain with Block, Inc.

* This document is released to provide transparency into the Bitkey product and service.

* Software is [licensed](LICENSE) by Block, Inc. under the MIT License (the “License”)  with the Commons Clause modifier, and with no warranties or guarantees.
