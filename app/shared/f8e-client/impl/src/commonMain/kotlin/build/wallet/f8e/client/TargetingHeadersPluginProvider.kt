package build.wallet.f8e.client

import build.wallet.account.analytics.AppInstallation
import build.wallet.analytics.v1.PlatformInfo
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import io.ktor.client.plugins.api.createClientPlugin

private object CustomHeaders {
  const val APP_VERSION = "Bitkey-App-Version"
  const val OS_TYPE = "Bitkey-OS-Type"
  const val OS_VERSION = "Bitkey-OS-Version"
  const val APP_INSTALLATION_ID = "Bitkey-App-Installation-ID"
  const val DEVICE_REGION = "Bitkey-Device-Region"
}

/**
 * Adds targeting headers to requests.
 * These headers are used when resolving any server side feature flags.
 */
@BitkeyInject(AppScope::class)
class TargetingHeadersPluginProvider(
  private val appInstallation: AppInstallation?,
  private val deviceRegion: String,
  private val platformInfo: PlatformInfo,
) {
  fun getPlugin() =
    createClientPlugin("TargetingHeadersPluginProvider") {
      onRequest { request, _ ->
        appInstallation?.let { request.headers.append(CustomHeaders.APP_INSTALLATION_ID, it.localId) }

        request.headers.append(CustomHeaders.APP_VERSION, platformInfo.application_version)
        request.headers.append(CustomHeaders.OS_TYPE, platformInfo.os_type.name)
        request.headers.append(CustomHeaders.OS_VERSION, platformInfo.os_version)
        request.headers.append(CustomHeaders.DEVICE_REGION, deviceRegion)
      }
    }
}
