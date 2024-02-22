package build.wallet.platform.haptics

interface HapticsPolicy {
  /**
   * Whether or not the phone is allowed to vibrate.
   */
  suspend fun canVibrate(): Boolean
}
