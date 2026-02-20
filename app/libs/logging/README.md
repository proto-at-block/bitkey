This module contains components and extensions for app logging.

We use [Kermit](https://kermit.touchlab.co/) library for performing and configuring logging.
Kermit exposes `LogWriter` interface which we implement for sending log outputs to Bugsnag and Datadog.

## Correlation

We attach `app_session_id` to Datadog log events. This value matches the `session_id` used by
`EventTracker` analytics events (via `AppSessionManager.getSessionId()`), and is useful for correlating
Datadog logs with screen impressions and other analytics.

This is separate from Datadog's own RUM session id, which is added automatically to logs when
`bundleWithRumEnabled` is enabled.
