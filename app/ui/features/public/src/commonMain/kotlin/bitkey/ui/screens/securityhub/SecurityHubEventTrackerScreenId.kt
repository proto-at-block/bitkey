package bitkey.ui.screens.securityhub

import build.wallet.analytics.events.screen.id.EventTrackerScreenId

enum class SecurityHubEventTrackerScreenId : EventTrackerScreenId {
  /** The main security hub screen */
  SECURITY_HUB_SCREEN,

  /** Education modal for EEK backup in security hub */
  SECURITY_HUB_EDUCATION_EEK_BACKUP,

  /** Education modal for fingerprints in security hub */
  SECURITY_HUB_EDUCATION_FINGERPRINTS,

  /** Education modal for social recovery in security hub */
  SECURITY_HUB_EDUCATION_SOCIAL_RECOVERY,

  /** Education modal for critical alerts in security hub */
  SECURITY_HUB_EDUCATION_CRITICAL_ALERTS,

  /** Education modal for transaction verification in security hub */
  SECURITY_HUB_EDUCATION_TRANSACTION_VERIFICATION,
}
