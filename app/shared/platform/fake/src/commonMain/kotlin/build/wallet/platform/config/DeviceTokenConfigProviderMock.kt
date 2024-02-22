package build.wallet.platform.config

class DeviceTokenConfigProviderMock : DeviceTokenConfigProvider {
  var configResult: DeviceTokenConfig? = null

  override suspend fun config() = configResult

  fun reset() {
    configResult = null
  }
}
