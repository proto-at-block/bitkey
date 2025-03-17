package build.wallet.analytics.events.screen.id

enum class InactiveAppEventTrackerScreenId : EventTrackerScreenId {
  /** Provides the customer with a choice to sign out other devices. */
  DECIDE_IF_SHOULD_ROTATE_AUTH,

  /** Loading screen shown when customer chooses to sign out other devices. */
  ROTATING_AUTH,

  /** Success screen shown when customer chooses to sign out other devices. */
  SUCCESSFULLY_ROTATED_AUTH,

  /**
   * Error screen shown when customer chooses to sign out other devices, but it fails.
   * This error is user-dismissible, as we know the rotation didn't work, but old keys still do.
   */
  FAILED_TO_ROTATE_AUTH_ACCEPTABLE,

  /**
   * Error screen shown when customer chooses to sign out other devices, but it fails.
   * This error is not user-dismissible, as we don't know the state of the keys.
   * The user can retry the rotation.
   */
  FAILED_TO_ROTATE_AUTH_UNEXPECTED,

  /**
   * Error screen shown when customer chooses to sign out other devices, but it fails.
   * This error is not user-dismissible, as we the backend rejects both old and new keys.
   * User probably has to go through recovery.
   */
  FAILED_TO_ROTATE_AUTH_ACCOUNT_LOCKED,

  /**
   * Loading that removes the proposal from persistence.
   */
  DISMISS_ROTATION_PROPOSAL,
}
