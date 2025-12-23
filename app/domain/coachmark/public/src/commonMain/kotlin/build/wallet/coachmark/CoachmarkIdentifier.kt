package build.wallet.coachmark

import build.wallet.analytics.v1.Action

/**
 * Coachmark identifiers
 */
enum class CoachmarkIdentifier(
  val value: String,
  val action: Action,
) {
  /** Add new coachmark identifiers here. These string values need to be stable or else coachmarks
   * will reappear for users who have already seen them. Do not change them!
   */
  SecurityHubSettingsCoachmark("security_hub_settings_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_SECURITY_HUB_SETTINGS),
  PrivateWalletSettingsCoachmark("private_wallet_settings_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_PRIVATE_WALLET_SETTINGS),
  PrivateWalletHomeCoachmark("private_wallet_home_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_PRIVATE_WALLET_HOME),
  Bip177Coachmark("bip_177_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_BIP_177),
}
