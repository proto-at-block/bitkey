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
  BitcoinPriceChartCoachmark("bitcoin_price_chart_coachmark", Action.ACTION_APP_COACHMARK_VIEWIED_BITCOIN_PRICE_CARD),
  InheritanceCoachmark("inheritance_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_INHERITANCE),
  SecurityHubSettingsCoachmark("security_hub_settings_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_SECURITY_HUB_SETTINGS),
  BalanceGraphCoachmark("balance_graph_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_BALANCE_GRAPH),
  SecurityHubHomeCoachmark("security_hub_coachmark", Action.ACTION_APP_COACHMARK_VIEWED_SECURITY_HUB_HOME),
}
