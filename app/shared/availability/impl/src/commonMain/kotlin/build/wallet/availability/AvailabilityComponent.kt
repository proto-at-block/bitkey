package build.wallet.availability

import build.wallet.account.analytics.AppInstallationDao
import build.wallet.analytics.events.PlatformInfoProvider
import build.wallet.datadog.DatadogTracer
import build.wallet.di.AppScope
import build.wallet.f8e.client.UnauthenticatedF8eHttpClientFactory
import build.wallet.f8e.client.UnauthenticatedOnlyF8eHttpClientImpl
import build.wallet.f8e.debug.NetworkingDebugService
import build.wallet.platform.config.AppVariant
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.settings.CountryCodeGuesser
import kotlinx.coroutines.CoroutineScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface AvailabilityComponent {
  // TODO: use @BitkeyInject. Currently there are some circular dependencies.
  @Provides
  fun provideF8eNetworkReachabilityService(
    appCoroutineScope: CoroutineScope,
    appInstallationDao: AppInstallationDao,
    appVariant: AppVariant,
    countryCodeGuesser: CountryCodeGuesser,
    datadogTracer: DatadogTracer,
    deviceInfoProvider: DeviceInfoProvider,
    networkingDebugService: NetworkingDebugService,
    platformInfoProvider: PlatformInfoProvider,
  ): F8eNetworkReachabilityService =
    F8eNetworkReachabilityServiceImpl(
      deviceInfoProvider = deviceInfoProvider,
      // TODO: break impl dependency.
      unauthenticatedF8eHttpClient = UnauthenticatedOnlyF8eHttpClientImpl(
        appCoroutineScope = appCoroutineScope,
        unauthenticatedF8eHttpClientFactory = UnauthenticatedF8eHttpClientFactory(
          appVariant = appVariant,
          platformInfoProvider = platformInfoProvider,
          appInstallationDao = appInstallationDao,
          countryCodeGuesser = countryCodeGuesser,
          datadogTracer = datadogTracer,
          deviceInfoProvider = deviceInfoProvider,
          networkReachabilityProvider = null,
          networkingDebugService = networkingDebugService
        )
      )
    )

  // TODO: use @BitkeyInject. Currently there are some circular dependencies.
  @Provides
  fun provideUnauthenticatedF8eHttpClientFactory(
    appCoroutineScope: CoroutineScope,
    appInstallationDao: AppInstallationDao,
    appVariant: AppVariant,
    countryCodeGuesser: CountryCodeGuesser,
    datadogTracer: DatadogTracer,
    deviceInfoProvider: DeviceInfoProvider,
    networkingDebugService: NetworkingDebugService,
    platformInfoProvider: PlatformInfoProvider,
    newNetworkReachabilityProvider: NewNetworkReachabilityProvider,
  ): UnauthenticatedF8eHttpClientFactory {
    // TODO: break impl dependency.
    return UnauthenticatedF8eHttpClientFactory(
      appVariant = appVariant,
      platformInfoProvider = platformInfoProvider,
      appInstallationDao = appInstallationDao,
      countryCodeGuesser = countryCodeGuesser,
      datadogTracer = datadogTracer,
      deviceInfoProvider = deviceInfoProvider,
      networkReachabilityProvider = newNetworkReachabilityProvider,
      networkingDebugService = networkingDebugService
    )
  }
}
