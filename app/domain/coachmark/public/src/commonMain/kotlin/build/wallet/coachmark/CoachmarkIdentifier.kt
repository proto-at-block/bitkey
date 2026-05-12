package build.wallet.coachmark

import build.wallet.analytics.v1.Action
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Coachmark identifiers
 *
 * Add new coachmark identifiers here. These string values need to be stable or else coachmarks
 * will reappear for users who have already seen them. Do not change them!
 */
enum class CoachmarkIdentifier(
  val id: String,
  val action: Action,
  val expiration: Duration? = null,
) {
  SecurityHubSettingsCoachmark(
    id = "security_hub_settings_coachmark",
    action = Action.ACTION_APP_COACHMARK_VIEWED_SECURITY_HUB_SETTINGS,
    expiration = 14.days
  ),
  PrivateWalletHomeCoachmark(
    id = "private_wallet_home_coachmark",
    action = Action.ACTION_APP_COACHMARK_VIEWED_PRIVATE_WALLET_HOME
  ),
  Bip177Coachmark(
    id = "bip_177_coachmark",
    action = Action.ACTION_APP_COACHMARK_VIEWED_BIP_177,
    expiration = 14.days
  ),
  @Deprecated("W3 upgrade completion now uses a sheet. Kept for DB row compatibility.")
  W3UpgradeCompleteCoachmark(
    id = "w3_upgrade_complete_coachmark",
    action = Action.ACTION_APP_COACHMARK_VIEWED_W3_UPGRADE_COMPLETE
  ),
  @Deprecated("Feature removed. Kept for DB row compatibility with older app versions.")
  AddressVerificationEducationCoachmark(
    id = "address_verification_education_coachmark",
    action = Action.ACTION_APP_COACHMARK_VIEWED_ADDRESS_VERIFICATION_EDUCATION
  ),
  W3UpgradeBlockerCoachmark(
    id = "w3_upgrade_blocker_coachmark",
    action = Action.ACTION_APP_COACHMARK_VIEWED_W3_UPGRADE_BLOCKER
  ),
}
