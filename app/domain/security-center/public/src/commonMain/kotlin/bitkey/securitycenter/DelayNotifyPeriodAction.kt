package bitkey.securitycenter

class DelayNotifyPeriodAction(
  val currentPeriodDays: Int,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> = emptyList()

  override fun category(): SecurityActionCategory = SecurityActionCategory.SECURITY

  override fun type(): SecurityActionType = SecurityActionType.DELAY_NOTIFY_PERIOD

  override fun state(): SecurityActionState = SecurityActionState.Secure
}
