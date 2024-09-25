package build.wallet.f8e.client

import build.wallet.account.analytics.AppInstallationDao
import build.wallet.analytics.events.PlatformInfoProvider
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.datadog.DatadogTracer
import build.wallet.f8e.debug.NetworkingDebugService
import build.wallet.platform.config.AppVariant
import build.wallet.platform.settings.CountryCodeGuesser
import io.ktor.client.engine.*

class UnauthenticatedF8eHttpClientFactory(
  appVariant: AppVariant,
  platformInfoProvider: PlatformInfoProvider,
  datadogTracer: DatadogTracer,
  appInstallationDao: AppInstallationDao,
  countryCodeGuesser: CountryCodeGuesser,
  networkReachabilityProvider: NetworkReachabilityProvider?,
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
  )
