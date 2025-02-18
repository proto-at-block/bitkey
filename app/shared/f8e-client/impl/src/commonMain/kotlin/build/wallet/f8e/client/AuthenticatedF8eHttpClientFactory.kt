package build.wallet.f8e.client

import build.wallet.account.analytics.AppInstallationDao
import build.wallet.analytics.events.PlatformInfoProvider
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.AuthTokensService
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.datadog.DatadogTracer
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.client.plugins.BitkeyAuthProvider
import build.wallet.f8e.client.plugins.ProofOfPossessionPlugin
import build.wallet.f8e.debug.NetworkingDebugService
import build.wallet.keybox.KeyboxDao
import build.wallet.platform.config.AppVariant
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.settings.CountryCodeGuesser
import io.ktor.client.*
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth

@BitkeyInject(AppScope::class, boundTypes = [AuthenticatedF8eHttpClientFactory::class])
class AuthenticatedF8eHttpClientFactory(
  appVariant: AppVariant,
  platformInfoProvider: PlatformInfoProvider,
  datadogTracer: DatadogTracer,
  deviceInfoProvider: DeviceInfoProvider,
  private val keyboxDao: KeyboxDao,
  private val authTokensService: AuthTokensService,
  private val appAuthKeyMessageSigner: AppAuthKeyMessageSigner,
  appInstallationDao: AppInstallationDao,
  countryCodeGuesser: CountryCodeGuesser,
  networkReachabilityProvider: NetworkReachabilityProvider,
  networkingDebugService: NetworkingDebugService,
  engine: HttpClientEngine? = null,
) : BaseF8eHttpClientFactory(
    appVariant = appVariant,
    platformInfoProvider = platformInfoProvider,
    datadogTracer = datadogTracer,
    deviceInfoProvider = deviceInfoProvider,
    appInstallationDao = appInstallationDao,
    countryCodeGuesser = countryCodeGuesser,
    networkReachabilityProvider = networkReachabilityProvider,
    networkingDebugService = networkingDebugService,
    engine = engine
  ) {
  override fun configureClient(config: HttpClientConfig<*>) {
    super.configureClient(config)
    val factory = this@AuthenticatedF8eHttpClientFactory
    with(config) {
      install(ProofOfPossessionPlugin) {
        keyboxDao = factory.keyboxDao
        authTokensService = factory.authTokensService
        appAuthKeyMessageSigner = factory.appAuthKeyMessageSigner
      }

      install(Auth) {
        providers.add(BitkeyAuthProvider(factory.authTokensService))
      }
    }
  }
}
