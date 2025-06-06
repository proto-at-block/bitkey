package build.wallet.inheritance

class InheritanceUpsellServiceFake : InheritanceUpsellService {
  private var seen = false

  override suspend fun markUpsellAsSeen() {
    seen = true
  }

  override suspend fun shouldShowUpsell(): Boolean {
    return !seen
  }

  override suspend fun reset() {
    seen = false
  }
}
