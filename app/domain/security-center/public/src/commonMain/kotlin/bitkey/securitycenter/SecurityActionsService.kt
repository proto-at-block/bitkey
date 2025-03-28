package bitkey.securitycenter

interface SecurityActionsService {
  /**
   * Returns a list of security actions for the given category.
   *
   * @param category the category of the action.
   */
  suspend fun getActions(category: SecurityActionCategory): List<SecurityAction>

  /**
   * Returns a list of recommended security actions for the customer.
   *
   */
  suspend fun getRecommendations(): List<SecurityActionRecommendation>
}
