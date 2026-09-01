package build.wallet.statemachine.root.metrics

import bitkey.metrics.MetricDefinition
import bitkey.metrics.MetricName
import bitkey.metrics.MetricOutcome

/**
 * Tracks age range verification checks that ALLOW the user to proceed, performed for
 * App Store Accountability Act compliance (Texas SB2420, etc.) during the
 * no-active-account (onboarding) flow.
 *
 * ## Privacy: denied checks intentionally emit NO metric
 * The age verification design guarantees "no tracking of blocked users" (see the Privacy
 * section of `docs/docs/design/age-restriction-app-store-accountability-act.md`). To honor
 * that, a Denied result does not start or complete this metric at all — there is no
 * `denied` variant. The block rate is instead approximated from views of the existing
 * AGE_RESTRICTED analytics screen ID (which already ships) relative to this metric's volume.
 *
 * ## Outcome semantics
 * The metric is recorded after the check result is known: for an Allowed result the metric
 * is started and immediately completed with [MetricOutcome.Succeeded]. A check that never
 * completes shows up as absent volume rather than a timeout.
 *
 * ## Platform split
 * Platform is intentionally NOT encoded in a variant: Datadog RUM already attaches
 * `source` (ios/android) and `os.*` attributes to every action, so platform behavior can be
 * split on those attributes directly.
 *
 * This metric is only emitted when the `age-range-verification-enabled` feature flag is
 * enabled (i.e. when a real platform age check can actually run), and never for the
 * Emergency (EEK) app variant, where the check is short-circuited.
 */
data object AgeRangeVerificationMetricDefinition : MetricDefinition {
  override val name = MetricName("age_range_verification")
}
