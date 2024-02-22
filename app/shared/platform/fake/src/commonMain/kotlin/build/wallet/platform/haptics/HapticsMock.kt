package build.wallet.platform.haptics

class HapticsMock : Haptics {
  override suspend fun vibrate(effect: HapticsEffect) = Unit
}
