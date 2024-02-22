package build.wallet.bitcoin.lightning

data class LightningPreferenceMock(
  var getResult: Boolean,
) : LightningPreference {
  override suspend fun get(): Boolean {
    return getResult
  }

  override suspend fun set(enabled: Boolean) {}
}
