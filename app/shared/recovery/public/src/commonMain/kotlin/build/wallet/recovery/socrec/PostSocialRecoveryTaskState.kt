package build.wallet.recovery.socrec

/**
 * States for follow-up actions after a social recovery is completed.
 */
enum class PostSocialRecoveryTaskState {
  /**
   * The user has just completed SocRec and should be directed to hardware
   * replacement screens.
   */
  HardwareReplacementScreens,

  /**
   * The user has previously completed social recovery, but has deferred
   * the hardware steps until later.
   */
  HardwareReplacementNotification,

  /**
   * There are no social recovery related follow-up actions to take.
   */
  None,
}
