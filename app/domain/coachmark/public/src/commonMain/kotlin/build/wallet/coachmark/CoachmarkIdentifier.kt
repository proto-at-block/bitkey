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
  InheritanceCoachmark("inheritance_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_INHERITANCE),
  SecurityHubSettingsCoachmark("security_hub_settings_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_SECURITY_HUB_SETTINGS),
  BalanceGraphCoachmark("balance_graph_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_BALANCE_GRAPH),
  SecurityHubHomeCoachmark("security_hub_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_SECURITY_HUB_HOME),
  PrivateWalletSettingsCoachmark("private_wallet_settings_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_PRIVATE_WALLET_SETTINGS),
  PrivateWalletHomeCoachmark("private_wallet_home_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_PRIVATE_WALLET_HOME),
}
