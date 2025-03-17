package build.wallet.analytics.events.screen

import build.wallet.analytics.v1.FingerprintScanStats

/**
 * Information related to [ACTION_HW_FINGERPRINT_SCAN_STATS] events
 */
data class EventTrackerFingerprintScanStatsInfo(
  val stats: FingerprintScanStats,
)
