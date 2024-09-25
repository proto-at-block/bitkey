package build.wallet.f8e.client.plugins

import build.wallet.account.analytics.AppInstallationDao
import build.wallet.platform.settings.CountryCodeGuesser
import com.github.michaelbull.result.get
import io.ktor.client.plugins.api.*

private object CustomHeaders {
  const val APP_VERSION = "Bitkey-App-Version"
  const val OS_TYPE = "Bitkey-OS-Type"
  const val OS_VERSION = "Bitkey-OS-Version"
  const val APP_INSTALLATION_ID = "Bitkey-App-Installation-ID"
  const val DEVICE_REGION = "Bitkey-Device-Region"
}

class TargetingHeadersPluginConfig {
  lateinit var appInstallationDao: AppInstallationDao
  lateinit var countryCodeGuesser: CountryCodeGuesser
}

/**
 * A Ktor Client Plugin that adds targeting headers to requests.
 * These headers are used when resolving any server side feature flags.
 */
val TargetingHeadersPlugin = createClientPlugin(
  "targeting-headers-plugin",
  ::TargetingHeadersPluginConfig
) {
  val appInstallationDao = pluginConfig.appInstallationDao
  val countryCodeGuesser = pluginConfig.countryCodeGuesser
  onRequest { request, _ ->
    appInstallationDao.getOrCreateAppInstallation().get()?.let { appInstallation ->
      request.headers.append(
        CustomHeaders.APP_INSTALLATION_ID,
        appInstallation.localId
      )
    }

    val platformInfo = request.attributes[PlatformInfoAttribute]
    request.headers.append(CustomHeaders.APP_VERSION, platformInfo.application_version)
    request.headers.append(CustomHeaders.OS_TYPE, platformInfo.os_type.name)
    request.headers.append(CustomHeaders.OS_VERSION, platformInfo.os_version)
    request.headers.append(CustomHeaders.DEVICE_REGION, countryCodeGuesser.countryCode())
  }
}
