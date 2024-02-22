This module contains components and extensions for app logging.

We use [Kermit](https://kermit.touchlab.co/) library for performing and configuring logging.
Kermit exposes `LogWriter` interface which we implement for sending log outputs to Bugsnag and Datadog.