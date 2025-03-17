package build.wallet.securityrecommendations

import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.statemachine.core.Icon

class FakeAction(
  private val isActionCompleted: Boolean,
  private val title: String,
  private val statusInfo: String? = null,
  private val icon: Icon,
  private val actionScreenId: NavigationScreenId,
  private val category: SecurityActionCategory,
) : SecurityAction {
  override fun isActionCompleted(): Boolean {
    return isActionCompleted
  }

  override fun getTitle(): String {
    return title
  }

  override fun getStatusInfo(): String? {
    return statusInfo
  }

  override fun getIcon(): Icon {
    return icon
  }

  override fun getActionScreenId(): NavigationScreenId {
    return actionScreenId
  }

  override fun category(): SecurityActionCategory {
    return category
  }
}
