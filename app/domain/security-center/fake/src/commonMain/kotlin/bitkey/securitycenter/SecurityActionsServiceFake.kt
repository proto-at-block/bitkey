package bitkey.securitycenter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SecurityActionsServiceFake(
  val actions: MutableList<SecurityAction> = mutableListOf(),
  val recommendations: MutableList<SecurityActionRecommendation> = actions.map {
    it.getRecommendations()
  }.flatten().toMutableList(),
) : SecurityActionsService {
  /**
   * Returns a list of security actions for the given category.
   *
   * @param category the category of the action.
   */
  override suspend fun getActions(category: SecurityActionCategory): List<SecurityAction> {
    return actions
  }

  /**
   * Returns a list of recommended security actions for the customer.
   *
   */

  override fun getRecommendations(): Flow<List<SecurityActionRecommendation>> {
    return flowOf(recommendations)
  }

  fun clear() {
    actions.clear()
    recommendations.clear()
  }
}
