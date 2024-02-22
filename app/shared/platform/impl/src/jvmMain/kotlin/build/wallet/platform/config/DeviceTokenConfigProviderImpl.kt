package build.wallet.platform.config

class DeviceTokenConfigProviderImpl(var deviceTokenConfig: DeviceTokenConfig? = null) : DeviceTokenConfigProvider {
  override suspend fun config(): DeviceTokenConfig? = deviceTokenConfig
}
