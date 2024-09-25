package build.wallet.f8e.client

import build.wallet.account.analytics.AppInstallationDao
import build.wallet.analytics.events.PlatformInfoProvider
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.AuthTokensRepository
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.datadog.DatadogTracer
import build.wallet.f8e.client.plugins.ProofOfPossessionPlugin
import build.wallet.f8e.debug.NetworkingDebugService
import build.wallet.keybox.KeyboxDao
import build.wallet.platform.config.AppVariant
import build.wallet.platform.settings.CountryCodeGuesser
import io.ktor.client.*
import io.ktor.client.engine.*

class AuthenticatedF8eHttpClientFactory(
  appVariant: AppVariant,
  platformInfoProvider: PlatformInfoProvider,
  datadogTracer: DatadogTracer,
  private val keyboxDao: KeyboxDao,
  private val authTokensRepository: AuthTokensRepository,
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
        authTokensRepository = factory.authTokensRepository
        appAuthKeyMessageSigner = factory.appAuthKeyMessageSigner
      }
    }
  }
}
