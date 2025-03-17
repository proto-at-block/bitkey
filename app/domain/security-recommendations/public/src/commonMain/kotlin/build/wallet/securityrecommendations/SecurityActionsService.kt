package build.wallet.securityrecommendations

interface SecurityActionsService {
  /**
   * Returns a list of recommended security actions for the customer. The actions are grouped by
   * categories defined in SecurityActionCategory.
   *
   * @param isCompleted true to fetch completed actions.
   */
  fun getRecommendedActions(isCompleted: Boolean): Map<SecurityActionCategory, List<SecurityAction>>
}
