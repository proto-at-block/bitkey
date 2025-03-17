package build.wallet.securityrecommendations

/**
 * Implementation of [SecurityActionsService].
 * @param actions list of security actions. Order of the list is important as it will be used to
 * determine the order of the actions in the UI.
 */
class SecurityActionsServiceImpl(
  private val actions: List<SecurityAction>,
) : SecurityActionsService {
  override fun getRecommendedActions(
    isCompleted: Boolean,
  ): Map<SecurityActionCategory, List<SecurityAction>> {
    val recommendedActions = actions.filter { it.isActionCompleted() == isCompleted }

    return recommendedActions.groupBy { it.category() }
  }
}
