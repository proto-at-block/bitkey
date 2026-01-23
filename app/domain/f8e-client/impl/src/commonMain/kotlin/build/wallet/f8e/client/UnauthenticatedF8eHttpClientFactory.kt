package build.wallet.f8e.client

import bitkey.datadog.DatadogTracer
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.analytics.events.PlatformInfoProvider
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.debug.NetworkingDebugService
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.platform.config.AppVariant
import build.wallet.platform.connectivity.InternetConnectionChecker
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.settings.CountryCodeGuesser
import io.ktor.client.engine.HttpClientEngine

@BitkeyInject(AppScope::class, boundTypes = [UnauthenticatedF8eHttpClientFactory::class])
class UnauthenticatedF8eHttpClientFactory(
  appVariant: AppVariant,
  platformInfoProvider: PlatformInfoProvider,
  datadogTracer: DatadogTracer,
  deviceInfoProvider: DeviceInfoProvider,
  appInstallationDao: AppInstallationDao,
  firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  countryCodeGuesser: CountryCodeGuesser,
  networkReachabilityProvider: NetworkReachabilityProvider,
  internetConnectionChecker: InternetConnectionChecker,
  networkingDebugService: NetworkingDebugService,
  engine: HttpClientEngine? = null,
) : BaseF8eHttpClientFactory(
    appVariant = appVariant,
    platformInfoProvider = platformInfoProvider,
    datadogTracer = datadogTracer,
    deviceInfoProvider = deviceInfoProvider,
    appInstallationDao = appInstallationDao,
    firmwareDeviceInfoDao = firmwareDeviceInfoDao,
    countryCodeGuesser = countryCodeGuesser,
    networkReachabilityProvider = networkReachabilityProvider,
    internetConnectionChecker = internetConnectionChecker,
    networkingDebugService = networkingDebugService,
    engine = engine
  )
