package bitkey.securitycenter

class SecurityActionsServiceFake : SecurityActionsService {
  /**
   * Returns a list of security actions for the given category.
   *
   * @param category the category of the action.
   */
  override suspend fun getActions(category: SecurityActionCategory): List<SecurityAction> {
    return emptyList()
  }

  /**
   * Returns a list of recommended security actions for the customer.
   *
   */
  override suspend fun getRecommendations(): List<SecurityActionRecommendation> {
    return emptyList()
  }
}
