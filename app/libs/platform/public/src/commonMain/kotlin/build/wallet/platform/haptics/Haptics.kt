package build.wallet.platform.haptics

interface Haptics {
  /** buzz buzz */
  suspend fun vibrate(effect: HapticsEffect)
}
