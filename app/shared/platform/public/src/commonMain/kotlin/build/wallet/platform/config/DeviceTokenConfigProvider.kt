package build.wallet.platform.config

/**
 * Do not use this provider directly, instead inject [DeviceTokenConfig], which is provided by [ActivityComponent].
 */
interface DeviceTokenConfigProvider {
  suspend fun config(): DeviceTokenConfig?
}
