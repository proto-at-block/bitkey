package build.wallet.securityrecommendations

import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.statemachine.core.Icon

/**
 * Represents a security action that the customer can take to improve their account's security.
 */
interface SecurityAction {
  /**
   * Returns true if the action has been completed by the customer.
   */
  fun isActionCompleted(): Boolean

  /**
   * Returns the title of the action. May vary depending on the state of the action.
   */
  fun getTitle(): String

  /**
   * Returns the description of the action. Dynamic based on the current status of the action.
   */
  fun getStatusInfo(): String?

  /**
   * Returns an icon for the action.
   */
  fun getIcon(): Icon

  /**
   * Returns the screen id to navigate to when the action is tapped.
   */
  fun getActionScreenId(): NavigationScreenId

  /**
   * Returns the category of the action.
   */
  fun category(): SecurityActionCategory
}

enum class SecurityActionCategory(val title: String) {
  ACCESS("Access"),
  RECOVERY("Recovery"),
}
